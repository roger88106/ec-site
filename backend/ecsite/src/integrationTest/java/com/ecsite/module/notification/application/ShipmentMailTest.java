package com.ecsite.module.notification.application;

import com.ecsite.module.notification.application.port.Mailer;
import com.ecsite.module.order.event.OrderShipped;
import com.ecsite.support.MutableClock;
import com.ecsite.support.async.AsyncEvents;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.http.Mailpit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.ecsite.support.async.AsyncEvents.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * 出荷通知メールの統合テスト
 *
 * <p>イベント「注文の出荷」から、出荷通知メールが 1 通だけ届くことと、送信に失敗したときの再試行を確認する。
 * 件名と本文の組み立ての細部は単体テスト、SMTP サーバーが応えないときの待ち時間の上限は {@code SmtpTimeoutTest} が確認する。
 *
 * <p>イベントをコミットして通知に届け、Testcontainers の Mailpit で受けたメールを確認する。
 * 送信はコミットの後に非同期で行われるので、結果を待つ。宛先をテストごとに変えて、他のテストのメールと混ぜない。
 */
@IntegrationTest
class ShipmentMailTest {

    // ============================== 定数 ==============================

    /**
     * イベントの発生日時
     *
     * <p>日本時間の 2026-10-08 10:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-08T01:00:00Z");

    /**
     * 注文番号
     */
    private static final String ORDER_NUMBER = "20261008-123456";

    /**
     * 送り状番号
     */
    private static final String TRACKING_NUMBER = "123456789012";

    /**
     * メールに書く、配送業者（ヤマト運輸）の表示名
     */
    private static final String CARRIER_NAME = "ヤマト運輸";

    /**
     * 送信元（統合テストの設定）
     */
    private static final String FROM = "no-reply@shop.example";

    // ============================== Bean ==============================

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate transaction;

    @Autowired
    Mailpit mailpit;

    @Autowired
    AsyncEvents asyncEvents;

    @Autowired
    MutableClock clock;

    @MockitoSpyBean
    Mailer mailer;

    /**
     * このテストの宛先（テストごとに変える）
     */
    private String email;

    @BeforeEach
    void setUp() {
        clock.setInstant(NOW);
        email = "ship-" + UUID.randomUUID() + "@example.com";
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("出荷通知メールの送信")
    class Send {

        @Test
        @DisplayName("AC-183 注文の出荷のイベントで、注文のメールアドレスに、注文番号・配送業者・送り状番号を書いた出荷通知メールが 1 通届く")
        void sendsShipmentMail() {
            // 操作：注文の出荷のイベントを発行する
            publish(shipped());

            // 確認：この宛先に 1 通届き、送信元・件名・本文が出荷通知メール
            await().atMost(TIMEOUT).until(() -> mailpit.messagesTo(email).size() == 1);
            assertShipmentMail(mailpit.messagesTo(email).getFirst());
        }

        @Test
        @DisplayName("AC-184 出荷のイベントを発行した処理は、メールの送信を待たない")
        void doesNotWaitForSending() {
            // 準備：送信を呼んだスレッドを記録する
            List<Thread> sendingThreads = new CopyOnWriteArrayList<>();
            doAnswer(invocation -> {
                sendingThreads.add(Thread.currentThread());
                return invocation.callRealMethod();
            }).when(mailer).send(any());

            // 操作：注文の出荷のイベントを発行する
            publish(shipped());

            // 確認：送信は、発行したスレッドとは別のスレッドで行われる
            // （送信の呼び出しを記録するまで待ってから比べる）
            verify(mailer, timeout(TIMEOUT.toMillis())).send(any());
            await().atMost(TIMEOUT).until(() -> !sendingThreads.isEmpty());
            assertThat(sendingThreads).singleElement().isNotSameAs(Thread.currentThread());
        }
    }

    @Nested
    @DisplayName("送信の失敗と再試行")
    class Retry {

        @Test
        @DisplayName("AC-184 SMTP が失敗したら送れず、未完了のイベントの再発行で送られる")
        void retriesFailedSend() {
            // 準備：1 回目の送信だけ、SMTP の失敗にする
            doThrow(new IllegalStateException("SMTP に接続できない")).doCallRealMethod().when(mailer).send(any());

            // 操作：注文の出荷のイベントを発行する
            var event = shipped();
            publish(event);

            // 確認：送信を試みたが、メールは届かない
            verify(mailer, timeout(TIMEOUT.toMillis())).send(any());
            assertThat(mailpit.messagesTo(email)).isEmpty();

            // 操作：失敗が記録されてから、再発行のジョブ（発行から 5 分より前のもの）と同じく、このテストのイベントを再発行する
            // （記録の前に再発行すると、対象にならない）
            asyncEvents.awaitFailed(event::equals);
            clock.advance(Duration.ofMinutes(6));
            asyncEvents.resubmit(event::equals);

            // 確認：メールが 1 通届く
            await().atMost(TIMEOUT).until(() -> mailpit.messagesTo(email).size() == 1);
        }

        @Test
        @DisplayName("AC-185 同じ注文の出荷のイベントを重ねて受けても、メールは 1 通だけ")
        void sendsOnlyOnce() {
            // 準備：イベントを発行し、メールが届いて発行の記録が完了するのを待つ
            // （同じイベントの処理が重なると、記録が完了にならないことがあるため。CatalogEventHandlerTest）
            var event = shipped();
            publish(event);
            await().atMost(TIMEOUT).until(() -> mailpit.messagesTo(email).size() == 1);
            asyncEvents.awaitHandled(event::equals);

            // 操作：同じイベントと、同じ注文の別のイベントを発行する
            publish(event);
            publish(OrderShipped.of(event.occurredAt(), event.orderId(), event.orderNumber(), event.email(),
                    event.carrier(), event.trackingNumber()));

            // 確認：処理が終わった後も、届いたメールは 1 通
            asyncEvents.awaitHandled(published -> published instanceof OrderShipped shipped && shipped.email().equals(email));
            assertThat(mailpit.messagesTo(email)).hasSize(1);
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 届いたメールが、出荷通知メールであることを確認する。
     *
     * <p>確認すること（期待値は定数）：
     * <ul>
     *   <li>送信元</li>
     *   <li>件名に注文番号がある</li>
     *   <li>本文に注文番号、配送業者の表示名、送り状番号がある</li>
     * </ul>
     *
     * @param message 届いたメール
     */
    private static void assertShipmentMail(Mailpit.Message message) {
        assertThat(message.from()).isEqualTo(FROM);
        assertThat(message.subject()).contains(ORDER_NUMBER);
        assertThat(message.text()).contains(ORDER_NUMBER, CARRIER_NAME, TRACKING_NUMBER);
    }

    // ============================== 準備の補助 ==============================

    /**
     * このテストの宛先への、ヤマト運輸の注文の出荷のイベントを作る。
     *
     * @return 注文の出荷
     */
    private OrderShipped shipped() {
        return OrderShipped.of(clock.instant(), UUID.randomUUID(), ORDER_NUMBER, email, OrderShipped.Carrier.YAMATO,
                TRACKING_NUMBER);
    }

    /**
     * イベントをトランザクションの中で発行し、コミットする（通知はコミットの後に受ける）。
     *
     * @param event 注文の出荷
     */
    private void publish(OrderShipped event) {
        transaction.executeWithoutResult(status -> events.publishEvent(event));
    }
}

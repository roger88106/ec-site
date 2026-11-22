package com.ecsite.module.notification.application;

import com.ecsite.common.model.id.OrderId;
import com.ecsite.module.notification.application.port.Mailer;
import com.ecsite.module.notification.application.port.OrderDirectory;
import com.ecsite.module.order.event.OrderShipped;
import com.ecsite.support.async.AsyncEvents;
import com.ecsite.support.config.IntegrationTest;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.ecsite.jooq.notification.Tables.SENT_MAIL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * SMTP サーバーが応えないときの統合テスト
 *
 * <p>接続を受けても何も返さない SMTP サーバーに向け、送信が読み取りの待ち時間の上限で失敗し（止まったままにならない）、
 * 送ったメールの記録が巻き戻ることを確認する。失敗したイベントを再発行で再送することは {@code ShipmentMailTest} が確認する。
 *
 * <p>メールの送信の宛先を書き換えるので、このクラスの後に Spring のコンテキストを捨てる。
 */
@IntegrationTest
@DirtiesContext
class SmtpTimeoutTest {

    // ============================== 定数 ==============================

    /**
     * 接続を受けるが、何も返さない SMTP サーバー
     */
    private static final SilentServer SILENT_SMTP = new SilentServer();

    /**
     * 送信の失敗を待つ時間の上限
     *
     * <p>設定の待ち時間の上限（10 秒）に、余裕を加える。
     */
    private static final Duration SEND_FAILURE_TIMEOUT = Duration.ofSeconds(40);

    /**
     * 出荷の発生日時
     */
    private static final Instant SHIPPED_AT = Instant.parse("2026-10-08T01:00:00Z");

    // ============================== Bean ==============================

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate transaction;

    @Autowired
    DSLContext dsl;

    @Autowired
    JavaMailSenderImpl mailSender;

    @Autowired
    AsyncEvents asyncEvents;

    @MockitoSpyBean
    Mailer mailer;

    @MockitoBean
    OrderDirectory orderDirectory;

    /**
     * メールの送信の宛先を、応えないサーバーにする（待ち時間の上限などの設定は、アプリの設定のまま）。
     */
    @BeforeEach
    void pointToSilentServer() {
        mailSender.setHost("localhost");
        mailSender.setPort(SILENT_SMTP.port());
    }

    @AfterAll
    static void stop() {
        SILENT_SMTP.close();
    }

    // ============================== テスト ==============================

    @Test
    @DisplayName("AC-184 SMTP サーバーが応えなければ、送信は読み取りの待ち時間の上限で失敗する")
    void failsWithinTimeout() {
        // 準備：送信の失敗を記録する
        var failures = recordFailures();

        // 操作：注文の出荷のイベントを発行する
        publishShipped(UUID.randomUUID());

        // 確認（前提）：上限の時間までに送信が失敗し、応えないサーバーが接続を受けていた
        await().atMost(SEND_FAILURE_TIMEOUT).until(() -> !failures.isEmpty());
        assertThat(SILENT_SMTP.accepted()).isPositive();

        // 確認：失敗の原因が、読み取りの待ち時間の上限
        assertThat(causalChain(failures.getFirst())).hasAtLeastOneElementOfType(SocketTimeoutException.class);
    }

    @Test
    @DisplayName("AC-184 送信が待ち時間の上限で失敗すると、送ったメールの記録は残らない")
    void rollsBackSentMailRecord() {
        // 準備：送信の失敗を記録する
        var failures = recordFailures();

        // 操作：注文の出荷のイベントを発行し、送信の失敗で、リスナーが失敗して終わるのを待つ
        var orderId = UUID.randomUUID();
        var event = publishShipped(orderId);
        await().atMost(SEND_FAILURE_TIMEOUT).until(() -> !failures.isEmpty());
        asyncEvents.awaitFailed(event::equals);

        // 確認：リスナーのトランザクションが終わった後に、この注文の送ったメールの記録がない
        assertThat(dsl.fetchCount(SENT_MAIL, SENT_MAIL.ORDER_ID.eq(orderId))).isZero();
    }

    // ============================== 確認の補助 ==============================

    /**
     * 例外と、その原因をたどった全ての例外を返す。
     *
     * @param failure 例外
     * @return 例外から順に、原因をたどった例外
     */
    private static List<Throwable> causalChain(Throwable failure) {
        var chain = new ArrayList<Throwable>();
        // 原因が自分を指すときに終わるよう、一度見た例外でたどるのを終える
        for (var current = failure; current != null && !chain.contains(current); current = current.getCause()) {
            chain.add(current);
        }
        return chain;
    }

    // ============================== 準備の補助 ==============================

    /**
     * メールの送信で投げられた例外を、記録するようにする。
     *
     * @return 記録した例外（送信の後に増える）
     */
    private List<Throwable> recordFailures() {
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            try {
                return invocation.callRealMethod();
            } catch (RuntimeException e) {
                failures.add(e);
                throw e;
            }
        }).when(mailer).send(any());
        return failures;
    }

    /**
     * 注文の出荷のイベントをトランザクションの中で発行し、コミットする。
     * 注文の腐敗防止層は、イベントの注文 ID に、このテストの宛先を返す。
     *
     * @param orderId 注文 ID（テストごとに作り、送ったメールの記録を自分の注文に絞る）
     * @return 発行したイベント
     */
    private OrderShipped publishShipped(UUID orderId) {
        when(orderDirectory.email(new OrderId(orderId))).thenReturn("timeout-" + orderId + "@example.com");
        var event = OrderShipped.of(SHIPPED_AT, new OrderId(orderId), "20261008-000001",
                OrderShipped.Carrier.YAMATO, "123456789012");
        transaction.executeWithoutResult(status -> events.publishEvent(event));
        return event;
    }

    /**
     * 接続を受けて、何も返さずに持ち続けるサーバー
     */
    private static final class SilentServer implements AutoCloseable {

        /**
         * 待ち受けのソケット
         */
        private final ServerSocket socket;

        /**
         * 受けた接続
         */
        private final List<Socket> connections = new CopyOnWriteArrayList<>();

        /**
         * 空いているポートで待ち受けを始める。
         */
        SilentServer() {
            try {
                socket = new ServerSocket(0);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            Thread.ofVirtual().start(() -> {
                while (!socket.isClosed()) {
                    try {
                        connections.add(socket.accept());
                    } catch (IOException e) {
                        return;
                    }
                }
            });
        }

        /**
         * 待ち受けのポートを返す。
         *
         * @return ポート
         */
        int port() {
            return socket.getLocalPort();
        }

        /**
         * 受けた接続の数を返す。
         *
         * @return 接続の数
         */
        int accepted() {
            return connections.size();
        }

        /**
         * 待ち受けと、受けた接続を閉じる。
         */
        @Override
        public void close() {
            try {
                socket.close();
                for (var connection : connections) {
                    connection.close();
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}

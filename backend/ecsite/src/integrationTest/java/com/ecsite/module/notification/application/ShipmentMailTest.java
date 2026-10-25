package com.ecsite.module.notification.application;

import com.ecsite.AsyncEvents;
import com.ecsite.IntegrationTest;
import com.ecsite.Mailpit;
import com.ecsite.MutableClock;
import com.ecsite.TestClockConfig;
import com.ecsite.module.notification.application.port.Mailer;
import com.ecsite.module.order.event.OrderShipped;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.ecsite.AsyncEvents.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * 出荷通知メールの統合テスト。<br />
 * イベント「注文の出荷」をコミットして通知に届け、Testcontainers の Mailpit で受けたメールを確認する。
 * 送信はコミットの後に非同期で行われるので、結果を待つ。宛先をテストごとに変えて、他のテストのメールと混ぜない。
 */
@IntegrationTest
@Import(TestClockConfig.class)
class ShipmentMailTest {

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate transaction;

    @Autowired
    IncompleteEventPublications incompletePublications;

    @Autowired
    Mailpit mailpit;

    @Autowired
    AsyncEvents asyncEvents;

    @Autowired
    MutableClock clock;

    @MockitoSpyBean
    Mailer mailer;

    private String email;

    @BeforeEach
    void setUp() {
        clock.setInstant(Instant.parse("2026-10-08T01:00:00Z"));
        email = "ship-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    @DisplayName("AC-183 注文の出荷のイベントで、注文のメールアドレスに出荷通知メールが 1 通届く")
    void sendsShipmentMail() {
        publish(shipped());

        await().atMost(TIMEOUT).until(() -> mailpit.messagesTo(email).size() == 1);
        var message = mailpit.messagesTo(email).getFirst();
        assertThat(message.from()).isEqualTo("no-reply@shop.example");
        assertThat(message.subject()).contains("20261008-123456");
        assertThat(message.text()).contains("20261008-123456", "ヤマト運輸", "123456789012");
    }

    @Test
    @DisplayName("AC-184 SMTP が失敗したら送れず、未完了のイベントの再発行で送られる")
    void retriesFailedSend() {
        doThrow(new IllegalStateException("SMTP に接続できない")).doCallRealMethod().when(mailer).send(any());

        publish(shipped());

        verify(mailer, timeout(TIMEOUT.toMillis())).send(any());
        assertThat(mailpit.messagesTo(email)).isEmpty();

        // 再発行のジョブ（発行から 5 分より前のもの）と同じ
        clock.advance(Duration.ofMinutes(6));
        incompletePublications.resubmitIncompletePublicationsOlderThan(Duration.ofMinutes(5));

        await().atMost(TIMEOUT).until(() -> mailpit.messagesTo(email).size() == 1);
    }

    @Test
    @DisplayName("AC-185 同じ注文の出荷のイベントを重ねて受けても、メールは 1 通だけ")
    void sendsOnlyOnce() {
        var event = shipped();
        publish(event);
        await().atMost(TIMEOUT).until(() -> mailpit.messagesTo(email).size() == 1);
        // 1 回目の発行の記録が完了してから重ねる（同じイベントの処理が重なると、記録が完了にならないことがある。CatalogEventHandlerTest）
        asyncEvents.awaitHandled(event::equals);

        publish(event);
        publish(OrderShipped.of(event.occurredAt(), event.orderId(), event.orderNumber(), event.email(),
                event.carrier(), event.trackingNumber()));

        asyncEvents.awaitHandled(published -> published instanceof OrderShipped shipped && shipped.email().equals(email));
        assertThat(mailpit.messagesTo(email)).hasSize(1);
    }

    // ============================== 補助 ==============================

    /**
     * このテストの宛先への、注文の出荷のイベント
     */
    private OrderShipped shipped() {
        return OrderShipped.of(clock.instant(), UUID.randomUUID(), "20261008-123456", email, OrderShipped.Carrier.YAMATO,
                "123456789012");
    }

    /**
     * イベントをトランザクションの中で発行し、コミットする（通知はコミットの後に受ける）。
     */
    private void publish(OrderShipped event) {
        transaction.executeWithoutResult(status -> events.publishEvent(event));
    }
}

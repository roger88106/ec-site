package com.ecsite.module.payment.application;

import com.ecsite.AsyncEvents;
import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.TestClockConfig;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.api.PaymentApi;
import com.ecsite.module.payment.api.PaymentSummary;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.events.IncompleteEventPublications;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.ecsite.AsyncEvents.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 与信の取消の統合テスト（T7211、AC-157、162、164、166）。<br />
 * 取消はコミットの後に非同期で行われるので、結果を待つ。
 */
@IntegrationTest
@Import(TestClockConfig.class)
class PaymentVoidTest {

    /**
     * 注文日時
     */
    private static final Instant ORDERED_AT = Instant.parse("2026-10-07T15:30:00Z");

    @Autowired
    PaymentApi paymentApi;

    @Autowired
    PaymentExpiryJob expiryJob;

    @Autowired
    TestPaymentGateway gateway;

    @Autowired
    IncompleteEventPublications incompletePublications;

    @Autowired
    MutableClock clock;

    @Autowired
    AsyncEvents asyncEvents;

    @Autowired
    DSLContext dsl;

    @BeforeEach
    void setUp() {
        clock.setInstant(ORDERED_AT);
        gateway.reset();
    }

    @Test
    @DisplayName("AC-162 与信済みの支払を取り消すと、コミットの後に決済サービスで取り消され、与信の取消済みになる")
    void voidsAuthorizedPayment() {
        var orderId = created();
        var pageId = paid(orderId);

        paymentApi.cancel(orderId);

        await().atMost(TIMEOUT).until(() -> statusOf(orderId) == PaymentSummary.Status.VOIDED);
        assertThat(gateway.voided()).containsExactly(gateway.authorizationRefOf(pageId));
    }

    @Test
    @DisplayName("AC-164、165 決済サービスで取消に失敗したら与信の取消待ちのまま残り、未完了のイベントの再発行で取り消される")
    void retriesFailedVoid() {
        var orderId = created();
        paid(orderId);
        gateway.failNextVoids(1);

        paymentApi.cancel(orderId);

        asyncEvents.awaitFailed(PaymentRequests.of(dsl, orderId));
        assertThat(statusOf(orderId)).isEqualTo(PaymentSummary.Status.VOID_PENDING);
        assertThat(gateway.voided()).isEmpty();

        // 再発行のジョブ（発行から 5 分より前のもの）と同じ
        clock.advance(Duration.ofMinutes(6));
        incompletePublications.resubmitIncompletePublicationsOlderThan(Duration.ofMinutes(5));

        await().atMost(TIMEOUT).until(() -> statusOf(orderId) == PaymentSummary.Status.VOIDED);
        assertThat(gateway.voided()).hasSize(1);
    }

    @Test
    @DisplayName("AC-166 取消済みの支払を重ねて取り消しても、成功し、決済サービスを呼び直さない")
    void ignoresRepeatedCancel() {
        var orderId = created();
        paid(orderId);
        paymentApi.cancel(orderId);
        await().atMost(TIMEOUT).until(() -> statusOf(orderId) == PaymentSummary.Status.VOIDED);

        paymentApi.cancel(orderId);

        asyncEvents.awaitHandled(PaymentRequests.of(dsl, orderId));
        assertThat(gateway.voided()).hasSize(1);
        assertThat(statusOf(orderId)).isEqualTo(PaymentSummary.Status.VOIDED);
    }

    @Test
    @DisplayName("未払いの支払を取り消すと支払なしになり、決済サービスは呼ばない")
    void cancelsUnpaidWithoutVoid() {
        var orderId = created();

        paymentApi.cancel(orderId);

        assertThat(statusOf(orderId)).isEqualTo(PaymentSummary.Status.NOT_PAID);
        asyncEvents.awaitHandled(PaymentRequests.of(dsl, orderId));
        assertThat(gateway.voided()).isEmpty();
    }

    @Test
    @DisplayName("AC-157 期限切れの後に与信の成功が届いたら、その与信を取り消す")
    void voidsLateAuthorization() {
        var orderId = created();
        var url = paymentApi.pageUrl(orderId).orElseThrow();
        var pageId = url.substring(url.lastIndexOf('/') + 1);
        clock.advance(Duration.ofMinutes(30));
        expiryJob.expire();
        assertThat(statusOf(orderId)).isEqualTo(PaymentSummary.Status.NOT_PAID);

        // 順序の入れ替わった通知
        gateway.notifyAuthorized(pageId);

        await().atMost(TIMEOUT).until(() -> statusOf(orderId) == PaymentSummary.Status.VOIDED);
        assertThat(gateway.voided()).containsExactly(gateway.authorizationRefOf(pageId));
    }

    // ============================== 補助 ==============================

    private UUID created() {
        var orderId = UUID.randomUUID();
        paymentApi.create(orderId, Money.of(6940), ORDERED_AT);
        return orderId;
    }

    /**
     * 支払の画面を開いて支払い、与信済みにする。
     */
    private String paid(UUID orderId) {
        var url = paymentApi.pageUrl(orderId).orElseThrow();
        var pageId = url.substring(url.lastIndexOf('/') + 1);
        gateway.pay(pageId);
        return pageId;
    }

    private PaymentSummary.Status statusOf(UUID orderId) {
        return paymentApi.find(List.of(orderId)).getFirst().status();
    }
}

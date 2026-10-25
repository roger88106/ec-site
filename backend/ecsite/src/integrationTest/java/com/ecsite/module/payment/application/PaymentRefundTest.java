package com.ecsite.module.payment.application;

import com.ecsite.AsyncEvents;
import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.PaymentTestData;
import com.ecsite.TestClockConfig;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.api.PaymentApi;
import com.ecsite.module.payment.api.PaymentSummary;
import com.ecsite.module.payment.domain.PaymentErrorCode;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.ecsite.AsyncEvents.TIMEOUT;
import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 返金の統合テスト（T9108、AC-188、192、193、194、195、BR-21）。<br />
 * 返金はコミットの後に非同期で行われるので、結果を待つ。
 */
@IntegrationTest
@Import(TestClockConfig.class)
class PaymentRefundTest {

    /**
     * 注文日時
     */
    private static final Instant ORDERED_AT = Instant.parse("2026-10-07T15:30:00Z");

    @Autowired
    PaymentApi paymentApi;

    @Autowired
    TestPaymentGateway gateway;

    @Autowired
    IncompleteEventPublications incompletePublications;

    @Autowired
    MutableClock clock;

    /**
     * 支払を進める補助
     */
    PaymentTestData payments;

    @Autowired
    AsyncEvents asyncEvents;

    @Autowired
    DSLContext dsl;

    @BeforeEach
    void setUp() {
        payments = new PaymentTestData(paymentApi, gateway);
        clock.setInstant(ORDERED_AT);
        gateway.reset();
    }

    @Test
    @DisplayName("AC-192、193 売上確定済みの支払を取り消すと返金待ちになり、コミットの後に決済サービスで返金されて、返金済みになる")
    void refundsCapturedPayment() {
        var orderId = payments.captured(ORDERED_AT);

        paymentApi.cancel(orderId);

        await().atMost(TIMEOUT).until(() -> payments.statusOf(orderId) == PaymentSummary.Status.REFUNDED);
        assertThat(gateway.refunded()).containsExactly(orderId);
    }

    @Test
    @DisplayName("AC-192 返金で決済サービスに渡す額は、その注文の売上確定の額（総額）と同じ")
    void refundsCapturedAmount() {
        var orderId = payments.captured(ORDERED_AT);

        paymentApi.cancel(orderId);

        await().atMost(TIMEOUT).until(() -> payments.statusOf(orderId) == PaymentSummary.Status.REFUNDED);
        assertThat(gateway.amountsOf(TestPaymentGateway.Operation.CAPTURE, orderId)).containsExactly(Money.of(6940));
        assertThat(gateway.amountsOf(TestPaymentGateway.Operation.REFUND, orderId))
                .isEqualTo(gateway.amountsOf(TestPaymentGateway.Operation.CAPTURE, orderId));
    }

    @Test
    @DisplayName("AC-306 返金のリスナーは、データベースのトランザクションの外で決済サービスを呼ぶ")
    void refundsOutsideTransaction() {
        var orderId = payments.captured(ORDERED_AT);
        var transactionActive = new CopyOnWriteArrayList<Boolean>();
        gateway.onRefundOrVoid(() -> transactionActive.add(TransactionSynchronizationManager.isActualTransactionActive()));

        paymentApi.cancel(orderId);

        await().atMost(TIMEOUT).until(() -> payments.statusOf(orderId) == PaymentSummary.Status.REFUNDED);
        assertThat(transactionActive).containsExactly(false);
    }

    @Test
    @DisplayName("AC-193、194 決済サービスが返金に失敗したら返金待ちのまま残り、未完了のイベントの再発行で返金される")
    void retriesFailedRefund() {
        var orderId = payments.captured(ORDERED_AT);
        gateway.failNextRefunds(1);

        paymentApi.cancel(orderId);

        asyncEvents.awaitFailed(PaymentRequests.of(dsl, orderId));
        assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.REFUND_PENDING);
        assertThat(gateway.refunded()).doesNotContain(orderId);

        // 再発行のジョブ（発行から 5 分より前のもの）と同じ
        clock.advance(Duration.ofMinutes(6));
        incompletePublications.resubmitIncompletePublicationsOlderThan(Duration.ofMinutes(5));

        await().atMost(TIMEOUT).until(() -> payments.statusOf(orderId) == PaymentSummary.Status.REFUNDED);
        assertThat(gateway.refunded()).containsOnlyOnce(orderId);
    }

    @Test
    @DisplayName("AC-195 返金済みの支払を重ねて取り消しても、成功し、返金は 1 回のまま変わらない")
    void ignoresRepeatedCancel() {
        var orderId = payments.captured(ORDERED_AT);
        paymentApi.cancel(orderId);
        await().atMost(TIMEOUT).until(() -> payments.statusOf(orderId) == PaymentSummary.Status.REFUNDED);

        paymentApi.cancel(orderId);

        asyncEvents.awaitHandled(PaymentRequests.of(dsl, orderId));
        assertThat(gateway.refunded()).hasSize(1);
        assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.REFUNDED);
    }

    @Test
    @DisplayName("AC-188 与信期限切れの支払を取り消しても、何もしない。返金も与信の取消も呼ばない")
    void cancelsAuthorizationExpiredWithoutRefund() {
        var orderId = payments.authorized(ORDERED_AT);
        gateway.expireAuthorization(gateway.authorizationRefOf(payments.pageIdOf(orderId)));
        assertBusinessError(() -> paymentApi.capture(orderId), PaymentErrorCode.AUTHORIZATION_EXPIRED);

        paymentApi.cancel(orderId);

        asyncEvents.awaitHandled(PaymentRequests.of(dsl, orderId));
        assertThat(gateway.refunded()).isEmpty();
        assertThat(gateway.voided()).isEmpty();
        assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.AUTHORIZATION_EXPIRED);
    }

    @Test
    @DisplayName("T9314 売上確定の最中に注文がキャンセルされたら、確定の反映で返金待ちになり、返金される。与信は取り消さない")
    void refundsWhenCancelledDuringCapture() {
        var orderId = payments.authorized(ORDERED_AT);
        var authorizationRef = gateway.authorizationRefOf(payments.pageIdOf(orderId));
        // 決済サービスが確定した後、確定を反映する前に、注文のキャンセルで支払が与信の取消待ちになる
        gateway.duringNextCapture(() -> paymentApi.cancel(orderId));

        paymentApi.capture(orderId);

        await().atMost(TIMEOUT).until(() -> payments.statusOf(orderId) == PaymentSummary.Status.REFUNDED);
        assertThat(gateway.refunded()).containsOnlyOnce(orderId);

        // 与信の取消の依頼は、再発行で何もせずに終わる
        clock.advance(Duration.ofMinutes(6));
        incompletePublications.resubmitIncompletePublicationsOlderThan(Duration.ofMinutes(5));
        asyncEvents.awaitHandled(PaymentRequests.of(dsl, orderId));
        assertThat(gateway.voided()).doesNotContain(authorizationRef);
        assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.REFUNDED);
    }
}

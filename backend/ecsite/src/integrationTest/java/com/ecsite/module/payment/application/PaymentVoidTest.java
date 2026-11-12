package com.ecsite.module.payment.application;

import com.ecsite.AsyncEvents;
import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.PaymentTestData;
import com.ecsite.TestClockConfig;
import com.ecsite.common.error.exception.SystemException;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.api.PaymentApi;
import com.ecsite.module.payment.api.model.PaymentSummary;
import com.ecsite.module.payment.domain.payment.VoidRequested;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.ecsite.AsyncEvents.TIMEOUT;
import static com.ecsite.jooq.payment.Tables.PAYMENT_;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * 与信の取消の統合テスト
 *
 * <p>取消はコミットの後に非同期で行われるので、結果を待つ。
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

    /**
     * 支払を進める補助
     */
    PaymentTestData payments;

    @Autowired
    AsyncEvents asyncEvents;

    @Autowired
    VoidRequestedHandler voidRequestedHandler;

    @Autowired
    DSLContext dsl;

    @BeforeEach
    void setUp() {
        payments = new PaymentTestData(paymentApi, gateway);
        clock.setInstant(ORDERED_AT);
        gateway.reset();
    }

    @Test
    @DisplayName("AC-162 与信済みの支払を取り消すと、コミットの後に決済サービスで取り消され、与信の取消済みになる")
    void voidsAuthorizedPayment() {
        var orderId = payments.created(ORDERED_AT);
        var pageId = payments.pay(orderId);

        paymentApi.cancel(orderId);

        await().atMost(TIMEOUT).until(() -> payments.statusOf(orderId) == PaymentSummary.Status.VOIDED);
        assertThat(gateway.voided()).containsExactly(gateway.authorizationRefOf(pageId));
    }

    @Test
    @DisplayName("AC-306 与信の取消のリスナーは、データベースのトランザクションの外で決済サービスを呼ぶ")
    void voidsOutsideTransaction() {
        var orderId = payments.created(ORDERED_AT);
        payments.pay(orderId);
        var transactionActive = new CopyOnWriteArrayList<Boolean>();
        gateway.onRefundOrVoid(() -> transactionActive.add(TransactionSynchronizationManager.isActualTransactionActive()));

        paymentApi.cancel(orderId);

        await().atMost(TIMEOUT).until(() -> payments.statusOf(orderId) == PaymentSummary.Status.VOIDED);
        assertThat(transactionActive).containsExactly(false);
    }

    @Test
    @DisplayName("AC-164、165 決済サービスで取消に失敗したら与信の取消待ちのまま残り、未完了のイベントの再発行で取り消される")
    void retriesFailedVoid() {
        var orderId = payments.created(ORDERED_AT);
        var authorizationRef = gateway.authorizationRefOf(payments.pay(orderId));
        gateway.failNextVoids(1);

        paymentApi.cancel(orderId);

        asyncEvents.awaitFailed(PaymentRequests.of(dsl, orderId));
        assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.VOID_PENDING);
        assertThat(gateway.voided()).doesNotContain(authorizationRef);

        // 再発行のジョブ（発行から 5 分より前のもの）と同じ
        clock.advance(Duration.ofMinutes(6));
        incompletePublications.resubmitIncompletePublicationsOlderThan(Duration.ofMinutes(5));

        await().atMost(TIMEOUT).until(() -> payments.statusOf(orderId) == PaymentSummary.Status.VOIDED);
        assertThat(gateway.voided()).containsOnlyOnce(authorizationRef);
    }

    @Test
    @DisplayName("AC-166 取消済みの支払を重ねて取り消しても、成功し、決済サービスを呼び直さない")
    void ignoresRepeatedCancel() {
        var orderId = payments.created(ORDERED_AT);
        payments.pay(orderId);
        paymentApi.cancel(orderId);
        await().atMost(TIMEOUT).until(() -> payments.statusOf(orderId) == PaymentSummary.Status.VOIDED);

        paymentApi.cancel(orderId);

        asyncEvents.awaitHandled(PaymentRequests.of(dsl, orderId));
        assertThat(gateway.voided()).hasSize(1);
        assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.VOIDED);
    }

    @Test
    @DisplayName("未払いの支払を取り消すと支払なしになり、決済サービスは呼ばない")
    void cancelsUnpaidWithoutVoid() {
        var orderId = payments.created(ORDERED_AT);

        paymentApi.cancel(orderId);

        assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.NOT_PAID);
        asyncEvents.awaitHandled(PaymentRequests.of(dsl, orderId));
        assertThat(gateway.voided()).isEmpty();
    }

    @Test
    @DisplayName("AC-157 期限切れの後に与信の成功が届いたら、その与信を取り消す")
    void voidsLateAuthorization() {
        var orderId = payments.created(ORDERED_AT);
        var pageId = payments.openPage(orderId);
        clock.advance(Duration.ofMinutes(30));
        expiryJob.expire();
        assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.NOT_PAID);

        // 順序の入れ替わった通知
        gateway.notifyAuthorized(pageId);

        await().atMost(TIMEOUT).until(() -> payments.statusOf(orderId) == PaymentSummary.Status.VOIDED);
        assertThat(gateway.voided()).containsExactly(gateway.authorizationRefOf(pageId));
    }

    @Test
    @DisplayName("AC-407-1 売上確定の応答が届かなかった支払の注文をキャンセルすると、返金済みになり、総額が 1 回だけ返金される。与信の取消済みにならない")
    void refundsCaptureWhoseResponseWasLost() {
        var orderId = capturedWithLostResponse();

        paymentApi.cancel(orderId);

        await().atMost(TIMEOUT).until(() -> payments.statusOf(orderId) == PaymentSummary.Status.REFUNDED);
        asyncEvents.awaitHandled(PaymentRequests.of(dsl, orderId));
        assertThat(gateway.voided()).isEmpty();
        assertThat(gateway.refunded()).containsExactly(orderId);
        assertThat(gateway.amountsOf(TestPaymentGateway.Operation.REFUND, orderId)).containsExactly(Money.of(6940));
    }

    @Test
    @DisplayName("AC-407-2 売上確定の応答が届かなかった支払で、与信の取消の依頼が重複して処理されても、返金は 1 回だけ行う")
    void refundsOnceWhenVoidRequestedTwice() {
        var orderId = capturedWithLostResponse();
        var paymentId = paymentIdOf(orderId);
        var duplicated = new AtomicBoolean();
        // 1 回目の取消の呼び出しの中で、重複した依頼を、支払が返金済みになるまで処理する。どちらの処理も、与信の取消待ちの状態の支払を読み込む
        gateway.onRefundOrVoid(() -> {
            if (duplicated.compareAndSet(false, true)) {
                AopTestUtils.<VoidRequestedHandler>getTargetObject(voidRequestedHandler)
                        .on(VoidRequested.of(clock.instant(), paymentId));
                await().atMost(TIMEOUT).until(() -> payments.statusOf(orderId) == PaymentSummary.Status.REFUNDED);
            }
        });

        paymentApi.cancel(orderId);

        await().atMost(TIMEOUT).until(() -> payments.statusOf(orderId) == PaymentSummary.Status.REFUNDED);
        asyncEvents.awaitHandled(PaymentRequests.of(dsl, orderId));
        assertThat(duplicated).isTrue();
        assertThat(gateway.amountsOf(TestPaymentGateway.Operation.REFUND, orderId)).hasSize(1);
        assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.REFUNDED);
        assertThat(gateway.voided()).isEmpty();
    }

    @Test
    @DisplayName("AC-407-3 売上確定の最中に注文がキャンセルされ、与信の取消が売上確定の反映より先に処理されても、返金は 1 回だけ行う")
    void refundsOnceWhenCancelledDuringCapture() {
        var orderId = payments.authorized(ORDERED_AT);
        gateway.duringNextCapture(() -> {
            paymentApi.cancel(orderId);
            await().atMost(TIMEOUT).until(() -> payments.statusOf(orderId) == PaymentSummary.Status.REFUNDED);
        });

        paymentApi.capture(orderId);

        asyncEvents.awaitHandled(PaymentRequests.of(dsl, orderId));
        assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.REFUNDED);
        assertThat(gateway.amountsOf(TestPaymentGateway.Operation.REFUND, orderId)).hasSize(1);
        assertThat(gateway.voided()).isEmpty();
    }

    // ============================== 補助 ==============================

    /**
     * 決済サービスで売上を確定したが応答が届かず、与信済みのままの支払を作る。
     *
     * @return 注文 ID
     */
    private UUID capturedWithLostResponse() {
        var orderId = payments.authorized(ORDERED_AT);
        gateway.loseNextCaptureResponses(1);
        assertThatThrownBy(() -> paymentApi.capture(orderId)).isInstanceOf(SystemException.class);
        assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.AUTHORIZED);
        return orderId;
    }

    /**
     * 注文の支払 ID を読む。
     *
     * @param orderId 注文 ID
     * @return 支払 ID
     */
    private UUID paymentIdOf(UUID orderId) {
        return dsl.select(PAYMENT_.ID).from(PAYMENT_).where(PAYMENT_.ORDER_ID.eq(orderId)).fetchOne(PAYMENT_.ID);
    }
}

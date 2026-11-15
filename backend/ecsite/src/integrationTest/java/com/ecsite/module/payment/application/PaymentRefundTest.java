package com.ecsite.module.payment.application;

import com.ecsite.AsyncEvents;
import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.PaymentTestData;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.api.PaymentApi;
import com.ecsite.module.payment.api.model.PaymentSummary;
import com.ecsite.module.payment.domain.PaymentErrorCode;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.ecsite.AsyncEvents.TIMEOUT;
import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 返金の統合テスト
 *
 * <p>返金はコミットの後に非同期で行われるので、結果を待つ。
 */
@IntegrationTest
class PaymentRefundTest {

    // ============================== 定数 ==============================

    /**
     * 注文日時
     *
     * <p>日本時間の 2026-10-08 00:30。
     */
    private static final Instant ORDERED_AT = Instant.parse("2026-10-07T15:30:00Z");

    /**
     * 支払の額（{@link PaymentTestData} が作る支払の総額）
     */
    private static final Money TOTAL = Money.of(6940);

    /**
     * 再発行のジョブが再発行する、発行からの経過時間（5 分）を超える長さ
     */
    private static final Duration PAST_RESUBMIT_AGE = Duration.ofMinutes(6);

    // ============================== Bean ==============================

    /**
     * 確認する決済の API
     */
    @Autowired
    PaymentApi paymentApi;

    /**
     * テスト用の決済サービス
     */
    @Autowired
    TestPaymentGateway gateway;

    /**
     * テストの時計
     */
    @Autowired
    MutableClock clock;

    /**
     * 非同期のイベントの処理を待つ補助
     */
    @Autowired
    AsyncEvents asyncEvents;

    /**
     * 支払 ID を読む DB
     */
    @Autowired
    DSLContext dsl;

    /**
     * 支払を進める補助
     */
    PaymentTestData payments;

    @BeforeEach
    void setUp() {
        payments = new PaymentTestData(paymentApi, gateway);
        clock.setInstant(ORDERED_AT);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("売上確定済みの支払の返金")
    class Refund {

        @Test
        @DisplayName("AC-192 売上確定済みの支払を取り消すと、決済サービスで返金されて、返金済みになる")
        void refundsCapturedPayment() {
            // 準備：売上確定済みの支払
            var orderId = payments.captured(ORDERED_AT);

            // 操作：
            paymentApi.cancel(orderId);

            // 確認：
            awaitRefunded(orderId);
            assertThat(gateway.refunded()).containsExactly(orderId);
        }

        @Test
        @DisplayName("AC-192 返金の額は、その注文の売上確定の額（総額）と同じ")
        void refundsCapturedAmount() {
            // 準備：売上確定済みの支払
            var orderId = payments.captured(ORDERED_AT);

            // 操作：
            paymentApi.cancel(orderId);

            // 確認：売上確定も返金も総額
            awaitRefunded(orderId);
            assertThat(gateway.amountsOf(TestPaymentGateway.Operation.CAPTURE, orderId)).containsExactly(TOTAL);
            assertThat(gateway.amountsOf(TestPaymentGateway.Operation.REFUND, orderId)).containsExactly(TOTAL);
        }

        @Test
        @DisplayName("AC-193、194 取消は返金の結果を待たずに終わり、終わった時点で支払は返金待ち")
        void cancelsWithoutWaitingForRefund() {
            // 準備：返金を、テストが許すまで待たせる
            var orderId = payments.captured(ORDERED_AT);
            var refundAllowed = new CountDownLatch(1);
            gateway.onRefundOrVoid(() -> awaitQuietly(refundAllowed));

            // 操作：
            paymentApi.cancel(orderId);

            // 確認：返金の前に取消が終わり、返金待ち
            assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.REFUND_PENDING);
            assertThat(gateway.refunded()).doesNotContain(orderId);

            // 操作：返金を許す
            refundAllowed.countDown();

            // 確認：その後に返金される
            awaitRefunded(orderId);
        }

        @Test
        @DisplayName("AC-306 返金のリスナーは、データベースのトランザクションの外で決済サービスを呼ぶ")
        void refundsOutsideTransaction() {
            // 準備：返金の呼び出しの時点で、トランザクションがあるかを記録する
            var orderId = payments.captured(ORDERED_AT);
            var transactionActive = new CopyOnWriteArrayList<Boolean>();
            gateway.onRefundOrVoid(() -> transactionActive.add(TransactionSynchronizationManager.isActualTransactionActive()));

            // 操作：
            paymentApi.cancel(orderId);

            // 確認：
            awaitRefunded(orderId);
            assertThat(transactionActive).containsExactly(false);
        }
    }

    @Nested
    @DisplayName("返金の失敗と再試行")
    class Retry {

        @Test
        @DisplayName("AC-193、194 決済サービスが返金に失敗しても取消は成功し、支払は返金待ちのまま残る")
        void staysRefundPendingWhenRefundFails() {
            // 準備：次の返金が失敗する
            var orderId = payments.captured(ORDERED_AT);
            gateway.failNextRefunds(1);

            // 操作：
            paymentApi.cancel(orderId);

            // 確認：返金の依頼の処理が失敗し、返金待ちのまま
            asyncEvents.awaitFailed(PaymentRequests.refund(dsl, orderId));
            assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.REFUND_PENDING);
            assertThat(gateway.refunded()).doesNotContain(orderId);
        }

        @Test
        @DisplayName("AC-194 失敗した返金は、未完了のイベントの再発行で返金され、返金済みになる")
        void retriesFailedRefund() {
            // 準備：返金に 1 回失敗している
            var orderId = payments.captured(ORDERED_AT);
            gateway.failNextRefunds(1);
            paymentApi.cancel(orderId);
            asyncEvents.awaitFailed(PaymentRequests.refund(dsl, orderId));

            // 操作：再発行のジョブと同じく、発行から 5 分より前のこのテストのイベントを再発行する
            clock.advance(PAST_RESUBMIT_AGE);
            asyncEvents.resubmit(PaymentRequests.of(dsl, orderId));

            // 確認：返金は 1 回
            awaitRefunded(orderId);
            assertThat(gateway.refunded()).containsOnlyOnce(orderId);
        }
    }

    @Nested
    @DisplayName("返金しない取消")
    class NoRefund {

        @Test
        @DisplayName("AC-195 返金済みの支払を重ねて取り消しても成功し、返金は 1 回のまま、返金済みのまま")
        void ignoresRepeatedCancel() {
            // 準備：返金済みの支払
            var orderId = payments.captured(ORDERED_AT);
            paymentApi.cancel(orderId);
            awaitRefunded(orderId);

            // 操作：もう一度取り消す
            paymentApi.cancel(orderId);

            // 確認：
            asyncEvents.awaitHandled(PaymentRequests.of(dsl, orderId));
            assertThat(gateway.refundsOf(orderId)).isEqualTo(1);
            assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.REFUNDED);
        }

        @Test
        @DisplayName("AC-188 与信期限切れの支払を取り消しても、返金も与信の取消も呼ばず、与信期限切れのまま")
        void cancelsAuthorizationExpiredWithoutRefund() {
            // 準備：売上確定で与信期限切れになった支払
            var orderId = payments.authorized(ORDERED_AT);
            var authorizationRef = gateway.authorizationRefOf(payments.pageIdOf(orderId));
            gateway.expireAuthorization(authorizationRef);
            assertBusinessError(() -> paymentApi.capture(orderId), PaymentErrorCode.AUTHORIZATION_EXPIRED);

            // 操作：
            paymentApi.cancel(orderId);

            // 確認：
            asyncEvents.awaitHandled(PaymentRequests.of(dsl, orderId));
            assertThat(gateway.refundsOf(orderId)).isZero();
            assertThat(gateway.voidsOf(authorizationRef)).isZero();
            assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.AUTHORIZATION_EXPIRED);
        }
    }

    @Nested
    @DisplayName("売上確定の最中のキャンセル")
    class CancelDuringCapture {

        @Test
        @DisplayName("AC-164 売上確定の最中に注文がキャンセルされたら、確定の反映で返金待ちになり、返金される")
        void refundsWhenCancelledDuringCapture() {
            // 準備：確定の最中にキャンセルされる
            var orderId = cancelledDuringCapture();

            // 操作：
            paymentApi.capture(orderId);

            // 確認：返金は 1 回
            awaitRefunded(orderId);
            assertThat(gateway.refunded()).containsOnlyOnce(orderId);
        }

        @Test
        @DisplayName("AC-164 売上確定の最中に注文がキャンセルされたら、与信の取消の依頼は再発行しても何もせずに終わる")
        void doesNotVoidWhenCancelledDuringCapture() {
            // 準備：確定の最中にキャンセルされ、返金済みになった
            var orderId = cancelledDuringCapture();
            var authorizationRef = gateway.authorizationRefOf(payments.pageIdOf(orderId));
            paymentApi.capture(orderId);
            awaitRefunded(orderId);

            // 操作：失敗した与信の取消の依頼を再発行する
            clock.advance(PAST_RESUBMIT_AGE);
            asyncEvents.resubmit(PaymentRequests.of(dsl, orderId));

            // 確認：与信を取り消さず、返金済みのまま
            asyncEvents.awaitHandled(PaymentRequests.of(dsl, orderId));
            assertThat(gateway.voided()).doesNotContain(authorizationRef);
            assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.REFUNDED);
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 注文の支払が返金済みになるまで待つ。
     *
     * @param orderId 注文 ID
     */
    private void awaitRefunded(UUID orderId) {
        await().atMost(TIMEOUT).until(() -> payments.statusOf(orderId) == PaymentSummary.Status.REFUNDED);
    }

    // ============================== 準備の補助 ==============================

    /**
     * 次の売上確定の最中に注文がキャンセルされる、与信済みの支払を作る。<br />
     * 与信の取消を 1 回失敗させ、確定の反映が与信の取消の処理より先になる順序に固定する。
     *
     * @return 注文 ID
     */
    private UUID cancelledDuringCapture() {
        var orderId = payments.authorized(ORDERED_AT);
        gateway.failNextVoids(1);
        // 決済サービスが確定した後、確定を反映する前に、注文のキャンセルで支払が与信の取消待ちになる
        gateway.duringNextCapture(() -> paymentApi.cancel(orderId));
        return orderId;
    }

    /**
     * 決済サービスの呼び出しを、許されるまで待たせる。<br />
     * 取消が返金を待つ不具合のときも、テストが止まり続けないように上限を付ける。
     *
     * @param allowed 許されたら 0 になる latch
     */
    private static void awaitQuietly(CountDownLatch allowed) {
        try {
            allowed.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

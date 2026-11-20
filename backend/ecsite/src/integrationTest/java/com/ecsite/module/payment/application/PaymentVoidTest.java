package com.ecsite.module.payment.application;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.api.PaymentApi;
import com.ecsite.module.payment.api.model.PaymentSummary;
import com.ecsite.module.payment.application.job.PaymentJobs;
import com.ecsite.module.payment.domain.PaymentErrorCode;
import com.ecsite.module.payment.domain.payment.VoidRequested;
import com.ecsite.module.payment.domain.payment.port.PaymentRepository;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import com.ecsite.support.MutableClock;
import com.ecsite.support.async.AsyncEvents;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.PaymentTestData;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.ecsite.support.BusinessErrorAssertions.assertSystemError;
import static com.ecsite.support.async.AsyncEvents.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 与信の取消の統合テスト
 *
 * <p>取消はコミットの後に非同期で行われるので、結果を待つ。
 */
@IntegrationTest
class PaymentVoidTest {

    // ============================== 定数 ==============================

    /**
     * 注文日時
     *
     * <p>日本時間の 2026-10-08 00:30。
     */
    private static final Instant ORDERED_AT = Instant.parse("2026-10-07T15:30:00Z");

    /**
     * 支払の期限（注文日時 ＋ 30 分）
     */
    private static final Instant DEADLINE = Instant.parse("2026-10-07T16:00:00Z");

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
     * 期限切れのジョブを取り出す Spring のコンテキスト
     */
    @Autowired
    ApplicationContext context;

    /**
     * 反映の操作を直接呼ぶ、支払の更新のサービス
     */
    @Autowired
    PaymentCommandService commandService;

    /**
     * 支払のバージョンを読むリポジトリ
     */
    @Autowired
    PaymentRepository paymentRepository;

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
     * 重複した与信の取消の依頼を直接処理する、返金と与信の取消のリスナー
     */
    @Autowired
    PaymentReversalHandler reversalHandler;

    /**
     * 支払 ID を読む DB
     */
    @Autowired
    DSLContext dsl;

    /**
     * 支払を進める補助
     */
    PaymentTestData payments;

    /**
     * 支払の操作を、呼んだ側のトランザクションに加えるためのテンプレート
     */
    @Autowired
    TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        payments = new PaymentTestData(paymentApi, gateway, paymentRepository, transactionTemplate);
        clock.setInstant(ORDERED_AT);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("与信済みの支払の取消")
    class AuthorizedVoid {

        @Test
        @DisplayName("AC-162 与信済みの支払を取り消すと、コミットの後に決済サービスで取り消され、与信の取消済みになる")
        void voidsAuthorizedPayment() {
            // 準備：与信済みの支払
            var orderId = payments.created(ORDERED_AT);
            var pageId = payments.pay(orderId);

            // 操作：注文の支払を取り消す
            cancel(orderId);

            // 確認：この支払の与信が取り消された
            awaitStatus(orderId, PaymentSummary.Status.VOIDED);
            assertThat(gateway.voided()).containsExactly(gateway.authorizationRefOf(pageId));
        }

        @Test
        @DisplayName("AC-306 与信の取消のリスナーは、データベースのトランザクションの外で決済サービスを呼ぶ")
        void voidsOutsideTransaction() {
            // 準備：与信の取消の呼び出しの時点で、トランザクションがあるかを記録する
            var orderId = payments.created(ORDERED_AT);
            payments.pay(orderId);
            var transactionActive = new CopyOnWriteArrayList<Boolean>();
            gateway.onRefundOrVoid(() -> transactionActive.add(TransactionSynchronizationManager.isActualTransactionActive()));

            // 操作：注文の支払を取り消す
            cancel(orderId);

            // 確認：与信の取消済みになり、取消の呼び出しはトランザクションの外の 1 回
            awaitStatus(orderId, PaymentSummary.Status.VOIDED);
            assertThat(transactionActive).containsExactly(false);
        }

        @Test
        @DisplayName("支払画面のない未払いの支払を取り消すと支払なしになり、決済サービスは呼ばない")
        void cancelsUnpaidWithoutVoid() {
            // 準備：未払いの支払
            var orderId = payments.created(ORDERED_AT);

            // 操作：注文の支払を取り消す
            cancel(orderId);

            // 確認：支払なしになり、決済サービスの与信の取消を呼ばない
            assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.NOT_PAID);
            asyncEvents.awaitHandled(PaymentRequests.of(dsl, orderId));
            assertThat(gateway.voided()).isEmpty();
        }

        @Test
        @DisplayName("AC-157 期限切れの後に与信の成功が届いたら、その与信を取り消す")
        void voidsLateAuthorization() {
            // 準備：画面を開いたまま期限切れになった支払
            var orderId = payments.created(ORDERED_AT);
            var pageId = payments.openPage(orderId);
            clock.setInstant(DEADLINE);
            PaymentJobs.expireUntil(context, () -> payments.statusOf(orderId) != PaymentSummary.Status.UNPAID);
            assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.NOT_PAID);

            // 操作：順序の入れ替わった与信の成功の通知が届く
            gateway.notifyAuthorized(pageId);

            // 確認：与信の取消済みになり、届いた与信が取り消された
            awaitStatus(orderId, PaymentSummary.Status.VOIDED);
            assertThat(gateway.voided()).containsExactly(gateway.authorizationRefOf(pageId));
        }
    }

    @Nested
    @DisplayName("与信の取消の失敗と再試行")
    class Retry {

        @Test
        @DisplayName("AC-164、165 決済サービスで取消に失敗したら、与信の取消待ちのまま残る")
        void staysVoidPendingWhenVoidFails() {
            // 準備：次の与信の取消が失敗する
            var orderId = payments.created(ORDERED_AT);
            var authorizationRef = gateway.authorizationRefOf(payments.pay(orderId));
            gateway.failNextVoids(1);

            // 操作：注文の支払を取り消す
            cancel(orderId);

            // 確認：与信の取消の依頼の処理が失敗し、与信の取消待ちのまま
            asyncEvents.awaitFailed(PaymentRequests.voidAuthorization(dsl, orderId));
            assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.VOID_PENDING);
            assertThat(gateway.voided()).doesNotContain(authorizationRef);
        }

        @Test
        @DisplayName("AC-164、165 失敗した与信の取消は、未完了のイベントの再発行で取り消され、与信の取消済みになる")
        void retriesFailedVoid() {
            // 準備：与信の取消に 1 回失敗している
            var orderId = payments.created(ORDERED_AT);
            var authorizationRef = gateway.authorizationRefOf(payments.pay(orderId));
            gateway.failNextVoids(1);
            cancel(orderId);
            asyncEvents.awaitFailed(PaymentRequests.voidAuthorization(dsl, orderId));

            // 操作：再発行のジョブと同じく、発行から 5 分より前のこのテストのイベントを再発行する
            clock.advance(PAST_RESUBMIT_AGE);
            asyncEvents.resubmit(PaymentRequests.of(dsl, orderId));

            // 確認：取消は 1 回
            awaitStatus(orderId, PaymentSummary.Status.VOIDED);
            assertThat(gateway.voided()).containsOnlyOnce(authorizationRef);
        }

        @Test
        @DisplayName("AC-166 取消済みの支払を重ねて取り消しても成功し、決済サービスを呼び直さず、取消済みのまま")
        void ignoresRepeatedCancel() {
            // 準備：与信が取消済みの支払
            var orderId = payments.created(ORDERED_AT);
            var authorizationRef = gateway.authorizationRefOf(payments.pay(orderId));
            cancel(orderId);
            awaitStatus(orderId, PaymentSummary.Status.VOIDED);

            // 操作：もう一度取り消す
            cancel(orderId);

            // 確認：取消は 1 回のまま、取消済みのまま
            asyncEvents.awaitHandled(PaymentRequests.of(dsl, orderId));
            assertThat(gateway.voidsOf(authorizationRef)).isEqualTo(1);
            assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.VOIDED);
        }

        @Test
        @DisplayName("取消済みの支払を重ねて取り消しても、支払のバージョンは進まない")
        void keepsVersionOnRepeatedCancel() {
            // 準備：与信が取消済みの支払と、そのときの支払のバージョン
            var orderId = payments.authorized(ORDERED_AT);
            cancel(orderId);
            awaitStatus(orderId, PaymentSummary.Status.VOIDED);
            asyncEvents.awaitHandled(PaymentRequests.of(dsl, orderId));
            var versionAfterVoid = payments.versionOf(orderId);

            // 操作：もう一度取り消す
            cancel(orderId);

            // 確認：リポジトリから再取得した支払のバージョンが同じ
            asyncEvents.awaitHandled(PaymentRequests.of(dsl, orderId));
            assertThat(payments.versionOf(orderId)).isEqualTo(versionAfterVoid);
        }

        @Test
        @DisplayName("与信が取り消された支払に、取消済みの反映が重ねて届いても、支払のバージョンは進まない")
        void keepsVersionOnRepeatedMarkVoided() {
            // 準備：与信が取消済みの支払と、そのときの支払のバージョン
            var orderId = payments.authorized(ORDERED_AT);
            cancel(orderId);
            awaitStatus(orderId, PaymentSummary.Status.VOIDED);
            asyncEvents.awaitHandled(PaymentRequests.of(dsl, orderId));
            var payment = paymentRepository.findByOrderId(orderId).orElseThrow();

            // 操作：取消済みの反映がもう一度届く
            commandService.markVoided(payment.id());

            // 確認：リポジトリから再取得した支払のバージョンが同じ
            assertThat(payments.versionOf(orderId)).isEqualTo(payment.version());
        }
    }

    @Nested
    @DisplayName("売上確定済みの与信の取消")
    class CapturedAuthorization {

        @Test
        @DisplayName("AC-407-1 売上確定の応答が届かなかった支払の注文をキャンセルすると、返金済みになり、総額が 1 回だけ返金される")
        void refundsCaptureWhoseResponseWasLost() {
            // 準備：売上確定の応答が届かなかった支払
            var orderId = capturedWithLostResponse();

            // 操作：注文の支払を取り消す
            cancel(orderId);

            // 確認：返金済みになり、総額が 1 回だけ返金された
            awaitStatus(orderId, PaymentSummary.Status.REFUNDED);
            asyncEvents.awaitHandled(PaymentRequests.of(dsl, orderId));
            assertThat(gateway.refunded()).containsExactly(orderId);
            assertThat(gateway.amountsOf(TestPaymentGateway.Operation.REFUND, orderId)).containsExactly(TOTAL);
        }

        @Test
        @DisplayName("AC-407-1 売上確定の応答が届かなかった支払の注文をキャンセルしても、決済サービスで与信を取り消さない")
        void doesNotVoidCaptureWhoseResponseWasLost() {
            // 準備：売上確定の応答が届かなかった支払
            var orderId = capturedWithLostResponse();

            // 操作：注文の支払を取り消す
            cancel(orderId);

            // 確認：返金済みになり、決済サービスで与信を取り消していない
            awaitStatus(orderId, PaymentSummary.Status.REFUNDED);
            asyncEvents.awaitHandled(PaymentRequests.of(dsl, orderId));
            assertThat(gateway.voidsOf(gateway.authorizationRefOf(payments.pageIdOf(orderId)))).isZero();
        }

        @Test
        @DisplayName("AC-407-2 売上確定の応答が届かなかった支払で、与信の取消の依頼が重複して処理されても、返金は 1 回だけ行う")
        void refundsOnceWhenVoidRequestedTwice() {
            // 準備：1 回目の取消の呼び出しの中で、重複した依頼を、支払が返金済みになるまで処理する。どちらの処理も、与信の取消待ちの支払を読み込む
            var orderId = capturedWithLostResponse();
            var paymentId = PaymentRequests.paymentIdOf(dsl, orderId);
            var duplicated = new AtomicBoolean();
            gateway.onRefundOrVoid(() -> {
                if (duplicated.compareAndSet(false, true)) {
                    AopTestUtils.<PaymentReversalHandler>getTargetObject(reversalHandler)
                            .on(VoidRequested.of(clock.instant(), paymentId));
                    awaitStatus(orderId, PaymentSummary.Status.REFUNDED);
                }
            });

            // 操作：注文の支払を取り消す
            cancel(orderId);

            // 確認：重複した依頼を処理し、返金は 1 回で、与信は取り消さない
            awaitStatus(orderId, PaymentSummary.Status.REFUNDED);
            asyncEvents.awaitHandled(PaymentRequests.of(dsl, orderId));
            assertThat(duplicated).isTrue();
            assertThat(gateway.amountsOf(TestPaymentGateway.Operation.REFUND, orderId)).hasSize(1);
            assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.REFUNDED);
            assertThat(gateway.voidsOf(gateway.authorizationRefOf(payments.pageIdOf(orderId)))).isZero();
        }

        @Test
        @DisplayName("AC-407-3 売上確定の最中に注文がキャンセルされ、与信の取消が売上確定の反映より先に処理されても、返金は 1 回だけ行う")
        void refundsOnceWhenCancelledDuringCapture() {
            // 準備：決済サービスが確定した後、確定を反映する前に、キャンセルと与信の取消の処理を終える
            var orderId = payments.authorized(ORDERED_AT);
            gateway.duringNextCapture(() -> {
                cancel(orderId);
                awaitStatus(orderId, PaymentSummary.Status.REFUNDED);
            });

            // 操作：売上を確定する
            paymentApi.capture(orderId);

            // 確認：返金は 1 回で、与信は取り消さない
            asyncEvents.awaitHandled(PaymentRequests.of(dsl, orderId));
            assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.REFUNDED);
            assertThat(gateway.amountsOf(TestPaymentGateway.Operation.REFUND, orderId)).hasSize(1);
            assertThat(gateway.voidsOf(gateway.authorizationRefOf(payments.pageIdOf(orderId)))).isZero();
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 注文の支払が指定の状態になるまで待つ。
     *
     * @param orderId 注文 ID
     * @param status  待つ状態
     */
    private void awaitStatus(UUID orderId, PaymentSummary.Status status) {
        await().atMost(TIMEOUT).until(() -> payments.statusOf(orderId) == status);
    }

    // ============================== 準備の補助 ==============================

    /**
     * 決済サービスで売上を確定したが応答が届かず、与信済みのままの支払を作る。
     *
     * @return 注文 ID
     */
    private UUID capturedWithLostResponse() {
        var orderId = payments.authorized(ORDERED_AT);
        gateway.loseNextCaptureResponses(1);
        assertSystemError(() -> paymentApi.capture(orderId), PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
        assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.AUTHORIZED);
        return orderId;
    }

    /**
     * 支払の取消を、トランザクションの中で呼ぶ（取消は呼んだ側のトランザクションに加わる）。
     *
     * @param orderId 注文 ID
     */
    private void cancel(UUID orderId) {
        transactionTemplate.executeWithoutResult(status -> paymentApi.cancel(orderId));
    }
}

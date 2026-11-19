package com.ecsite.module.payment.application;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.api.PaymentApi;
import com.ecsite.module.payment.api.model.PaymentSummary;
import com.ecsite.module.payment.application.job.PaymentJobs;
import com.ecsite.module.payment.domain.PaymentErrorCode;
import com.ecsite.module.payment.domain.payment.port.PaymentRepository;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import com.ecsite.support.MutableClock;
import com.ecsite.support.async.AsyncEvents;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.PaymentTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.ecsite.support.BusinessErrorAssertions.assertBusinessError;
import static com.ecsite.support.BusinessErrorAssertions.assertSystemError;
import static com.ecsite.support.async.AsyncEvents.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 売上確定の統合テスト
 *
 * <p>決済サービスは mock。支払はトランザクションを確定させて保存するので、テストごとに別の注文 ID を使う。
 */
@IntegrationTest
class PaymentCaptureTest {

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
     * テストの時計
     */
    @Autowired
    MutableClock clock;

    /**
     * 決済の設定（照合を始めるまでの時間）
     */
    @Autowired
    PaymentProperties properties;

    /**
     * 期限切れのジョブを実行するための Spring のコンテキスト
     */
    @Autowired
    ApplicationContext context;

    /**
     * 非同期のイベントの処理を待つ補助
     */
    @Autowired
    AsyncEvents asyncEvents;

    /**
     * 支払を進める補助
     */
    PaymentTestData payments;

    /**
     * 支払の作成を、呼んだ側のトランザクションに加えるためのテンプレート
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
    @DisplayName("与信済みの支払の売上確定")
    class Authorized {

        @Test
        @DisplayName("AC-168、BR-21 与信済みの支払を確定すると、売上確定済みになる")
        void becomesCaptured() {
            // 準備：与信済みの支払
            var orderId = payments.authorized(ORDERED_AT);

            // 操作：売上を確定する
            paymentApi.capture(orderId);

            // 確認：売上確定済み
            assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.CAPTURED);
        }

        @Test
        @DisplayName("AC-168、BR-21 与信済みの支払を確定すると、決済サービスで売上が確定する")
        void capturesAtGateway() {
            // 準備：与信済みの支払
            var orderId = payments.authorized(ORDERED_AT);

            // 操作：売上を確定する
            paymentApi.capture(orderId);

            // 確認：決済サービスで、この注文の売上だけが確定している
            assertThat(gateway.captured()).containsExactly(orderId);
        }

        @Test
        @DisplayName("AC-168 決済サービスで確定する売上の額は、支払の総額")
        void capturesTotal() {
            // 準備：与信済みの支払
            var orderId = payments.authorized(ORDERED_AT);

            // 操作：売上を確定する
            paymentApi.capture(orderId);

            // 確認：決済サービスで確定した額は、支払の総額の 1 件
            assertThat(gateway.amountsOf(TestPaymentGateway.Operation.CAPTURE, orderId)).containsExactly(TOTAL);
        }
    }

    @Nested
    @DisplayName("重ねた売上確定")
    class Repeated {

        @Test
        @DisplayName("AC-172 売上確定済みの支払を重ねて確定しても成功し、売上確定済みのまま")
        void staysCaptured() {
            // 準備：売上確定済みの支払
            var orderId = payments.authorized(ORDERED_AT);
            paymentApi.capture(orderId);

            // 操作：もう一度確定する
            paymentApi.capture(orderId);

            // 確認：売上確定済みのまま
            assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.CAPTURED);
        }

        @Test
        @DisplayName("AC-172 売上確定済みの支払を重ねて確定しても、決済サービスを呼ばない")
        void callsGatewayOnce() {
            // 準備：売上確定済みの支払
            var orderId = payments.authorized(ORDERED_AT);
            paymentApi.capture(orderId);

            // 操作：もう一度確定する
            paymentApi.capture(orderId);

            // 確認：決済サービスの呼び出しは、1 回目の確定の 1 回だけ
            assertThat(gateway.captureCallsOf(orderId)).isEqualTo(1);
        }

        @Test
        @DisplayName("売上確定済みの支払に売上確定の反映が重ねて届いても、支払のバージョンは進まない")
        void keepsVersionOnRepeatedMarkCaptured() {
            // 準備：売上確定済みの支払と、そのときの支払のバージョン
            var orderId = payments.captured(ORDERED_AT);
            var payment = paymentRepository.findByOrderId(orderId).orElseThrow();
            var versionAfterCapture = payment.version();

            // 操作：売上確定の反映がもう一度届く
            commandService.markCaptured(payment.id());

            // 確認：リポジトリから再取得した支払のバージョンが同じ
            assertThat(payments.versionOf(orderId))
                    .isEqualTo(versionAfterCapture);
        }
    }

    @Nested
    @DisplayName("与信期限切れ")
    class AuthorizationExpired {

        @Test
        @DisplayName("与信済みでない支払に与信期限切れの反映が届いても、支払のバージョンは進まない")
        void keepsVersionWhenNotAuthorized() {
            // 準備：売上確定済みの支払と、そのときの支払のバージョン
            var orderId = payments.captured(ORDERED_AT);
            var payment = paymentRepository.findByOrderId(orderId).orElseThrow();
            var versionAfterCapture = payment.version();

            // 操作：与信期限切れの反映が届く
            commandService.markAuthorizationExpired(payment.id());

            // 確認：リポジトリから再取得した支払のバージョンが同じ
            assertThat(payments.versionOf(orderId))
                    .isEqualTo(versionAfterCapture);
        }

        @Test
        @DisplayName("AC-169 決済サービスが与信期限切れで断ったら、業務のエラーを返す")
        void returnsBusinessError() {
            // 準備：決済サービスで与信が切れている
            var orderId = withExpiredAuthorization();

            // 操作と確認：与信期限切れの業務のエラー
            assertBusinessError(() -> paymentApi.capture(orderId), PaymentErrorCode.AUTHORIZATION_EXPIRED);
        }

        @Test
        @DisplayName("AC-169 決済サービスが与信期限切れで断ったら、与信期限切れになり、売上は確定しない")
        void becomesAuthorizationExpired() {
            // 準備：決済サービスで与信が切れている
            var orderId = withExpiredAuthorization();

            // 操作：確定する（業務のエラーになる）
            assertBusinessError(() -> paymentApi.capture(orderId), PaymentErrorCode.AUTHORIZATION_EXPIRED);

            // 確認：与信期限切れで、決済サービスで確定した売上はない
            assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.AUTHORIZATION_EXPIRED);
            assertThat(gateway.captured()).isEmpty();
        }

        @Test
        @DisplayName("AC-169 与信期限切れの支払を重ねて確定しても、同じ業務のエラーを返す")
        void returnsSameErrorAgain() {
            // 準備：与信期限切れの支払
            var orderId = authorizationExpired();

            // 操作と確認：もう一度確定すると、同じ与信期限切れの業務のエラー
            assertBusinessError(() -> paymentApi.capture(orderId), PaymentErrorCode.AUTHORIZATION_EXPIRED);
        }

        @Test
        @DisplayName("AC-169 与信期限切れの支払を重ねて確定しても、決済サービスを呼ばない")
        void doesNotCallGatewayAgain() {
            // 準備：与信期限切れの支払
            var orderId = authorizationExpired();

            // 操作：もう一度確定する（業務のエラーになる）
            assertBusinessError(() -> paymentApi.capture(orderId), PaymentErrorCode.AUTHORIZATION_EXPIRED);

            // 確認：決済サービスの呼び出しは、1 回目の確定の 1 回だけ
            assertThat(gateway.captureCallsOf(orderId)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("決済サービスに接続できない")
    class Unreachable {

        @Test
        @DisplayName("AC-169、NFR-10 決済サービスに接続できなければ、システムのエラーを返す")
        void returnsSystemError() {
            // 準備：次の売上確定で接続できない
            var orderId = payments.authorized(ORDERED_AT);
            gateway.failNextCaptures(1);

            // 操作と確認：接続できないシステムのエラー
            assertSystemError(() -> paymentApi.capture(orderId), PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
        }

        @Test
        @DisplayName("AC-169、NFR-10 決済サービスに接続できなければ、与信済みのまま")
        void staysAuthorized() {
            // 準備：次の売上確定で接続できない
            var orderId = payments.authorized(ORDERED_AT);
            gateway.failNextCaptures(1);

            // 操作：確定する（システムのエラーになる）
            assertSystemError(() -> paymentApi.capture(orderId), PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);

            // 確認：与信済みのまま
            assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.AUTHORIZED);
        }

        @Test
        @DisplayName("AC-169、NFR-10 接続できずに失敗した後、つなぎ直せば確定できる")
        void capturesAfterReconnect() {
            // 準備：1 回目の売上確定が、接続できずに失敗している
            var orderId = payments.authorized(ORDERED_AT);
            gateway.failNextCaptures(1);
            assertSystemError(() -> paymentApi.capture(orderId), PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);

            // 操作：もう一度確定する
            paymentApi.capture(orderId);

            // 確認：売上確定済みになり、決済サービスで確定した
            assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.CAPTURED);
            assertThat(gateway.captured()).containsExactly(orderId);
        }
    }

    @Nested
    @DisplayName("売上確定を求めた時刻の記録")
    class CaptureRequestRecord {

        @Test
        @DisplayName("売上確定を求めると、決済サービスの応答を待つ間に、求めた時刻がコミットされている")
        void recordsBeforeGatewayResponse() {
            // 準備：決済サービスが確定した後、応答を返す前に、別のトランザクションで読む
            var orderId = payments.authorized(ORDERED_AT);
            var recordedDuring = new AtomicReference<Optional<Instant>>();
            gateway.duringNextCapture(() -> recordedDuring.set(captureRequestedAtOf(orderId)));

            // 操作：売上を確定する
            paymentApi.capture(orderId);

            // 確認：応答を待つ間に、求めた時刻が読めた
            assertThat(recordedDuring.get()).contains(ORDERED_AT);
        }

        @Test
        @DisplayName("決済サービスに接続できずに失敗しても、売上確定を求めた時刻は残る")
        void keepsRequestWhenUnreachable() {
            // 準備：次の売上確定で接続できない
            var orderId = payments.authorized(ORDERED_AT);
            gateway.failNextCaptures(1);

            // 操作：確定する（システムのエラーになる）
            assertSystemError(() -> paymentApi.capture(orderId), PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);

            // 確認：与信済みのままで、求めた時刻は残る
            assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.AUTHORIZED);
            assertThat(captureRequestedAtOf(orderId)).contains(ORDERED_AT);
        }

        @Test
        @DisplayName("売上確定済みの支払を重ねて確定しても、求めた時刻は変わらない")
        void keepsRequestTimeOnRepeat() {
            // 準備：売上確定済みの支払
            var orderId = payments.captured(ORDERED_AT);

            // 操作：時間が過ぎた後に、もう一度確定する
            clock.setInstant(ORDERED_AT.plusSeconds(60));
            paymentApi.capture(orderId);

            // 確認：最初に求めた時刻のまま
            assertThat(captureRequestedAtOf(orderId)).contains(ORDERED_AT);
        }

        @Test
        @DisplayName("接続できずに失敗した後に確定し直すと、求めた時刻は新しい時刻になる")
        void updatesRequestTimeOnRetry() {
            // 準備：1 回目の売上確定が、接続できずに失敗している
            var orderId = payments.authorized(ORDERED_AT);
            gateway.failNextCaptures(1);
            assertSystemError(() -> paymentApi.capture(orderId), PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);

            // 操作：時間が過ぎた後に、もう一度確定し、また接続できない
            var retriedAt = ORDERED_AT.plusSeconds(60);
            clock.setInstant(retriedAt);
            gateway.failNextCaptures(1);
            assertSystemError(() -> paymentApi.capture(orderId), PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);

            // 確認：新しい時刻になる
            assertThat(captureRequestedAtOf(orderId)).contains(retriedAt);
        }
    }

    @Nested
    @DisplayName("照合で売上確定済みにした支払")
    class ReconciledCaptured {

        @Test
        @DisplayName("照合で売上確定済みにした支払は、売上確定が成功し、決済サービスを呼ばない")
        void capturesWithoutGateway() {
            // 準備：売上確定の応答が届かず、照合で売上確定済みにした支払
            var orderId = reconciledCaptured();

            // 操作：売上を確定する（出荷の登録）
            paymentApi.capture(orderId);

            // 確認：売上確定済みで、決済サービスへの売上確定は、応答が届かなかった 1 回だけ
            assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.CAPTURED);
            assertThat(gateway.captureCallsOf(orderId)).isEqualTo(1);
        }

        @Test
        @DisplayName("照合で売上確定済みにした支払を取り消すと、総額を 1 回だけ返金し、与信は取り消さない")
        void refundsTotalOnce() {
            // 準備：売上確定の応答が届かず、照合で売上確定済みにした支払
            var orderId = reconciledCaptured();
            var authorizationRef = gateway.authorizationRefOf(payments.pageIdOf(orderId));

            // 操作：注文の支払を取り消す（注文のキャンセル）
            transactionTemplate.executeWithoutResult(status -> paymentApi.cancel(orderId));

            // 確認：返金済みになり、総額が 1 回だけ返金され、確定した与信は取り消さない
            await().atMost(TIMEOUT).until(() -> payments.statusOf(orderId) == PaymentSummary.Status.REFUNDED);
            assertThat(gateway.refunded()).containsExactly(orderId);
            assertThat(gateway.amountsOf(TestPaymentGateway.Operation.REFUND, orderId)).containsExactly(TOTAL);
            assertThat(gateway.voidsOf(authorizationRef)).isZero();
        }
    }

    @Nested
    @DisplayName("確定できない支払")
    class NotCapturable {

        @Test
        @DisplayName("BR-21 与信済みでない支払（未払い）は確定できない")
        void rejectsUnpaid() {
            // 準備：未払いの支払
            var orderId = payments.created(ORDERED_AT);

            // 操作と確認：確定できない業務のエラー
            assertBusinessError(() -> paymentApi.capture(orderId), PaymentErrorCode.PAYMENT_NOT_CAPTURABLE);
        }

        @Test
        @DisplayName("BR-21 与信済みでない支払（未払い）の確定は、未払いのまま、決済サービスも呼ばない")
        void keepsUnpaidWithoutGateway() {
            // 準備：未払いの支払
            var orderId = payments.created(ORDERED_AT);

            // 操作：確定する（業務のエラーになる）
            assertBusinessError(() -> paymentApi.capture(orderId), PaymentErrorCode.PAYMENT_NOT_CAPTURABLE);

            // 確認：未払いのままで、決済サービスの売上確定を呼ばない
            assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.UNPAID);
            assertThat(gateway.captureCallsOf(orderId)).isZero();
        }

        @Test
        @DisplayName("BR-21 支払のない注文は確定できず、決済サービスも呼ばない")
        void rejectsUnknownOrder() {
            // 準備：支払のない注文
            var orderId = UUID.randomUUID();

            // 操作と確認：見つからないエラー
            assertBusinessError(() -> paymentApi.capture(orderId), CommonErrorCode.NOT_FOUND);

            // 確認：決済サービスの売上確定を呼ばない
            assertThat(gateway.captureCallsOf(orderId)).isZero();
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 注文の支払の、売上確定を求めた時刻を読む。
     *
     * @param orderId 注文 ID
     * @return 売上確定を求めた時刻。なければ空
     */
    private Optional<Instant> captureRequestedAtOf(UUID orderId) {
        return paymentRepository.findByOrderId(orderId).orElseThrow().captureRequestedAt();
    }

    // ============================== 準備の補助 ==============================

    /**
     * 売上確定の応答が届かず、照合を始めるまでの時間の後に、期限切れのジョブが売上確定済みにした支払を作る。
     *
     * @return 注文 ID
     */
    private UUID reconciledCaptured() {
        var orderId = payments.authorized(ORDERED_AT);
        gateway.loseNextCaptureResponses(1);
        assertSystemError(() -> paymentApi.capture(orderId), PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
        clock.setInstant(ORDERED_AT.plus(properties.captureReconcileDelay()));
        PaymentJobs.expireUntil(context, () -> payments.statusOf(orderId) == PaymentSummary.Status.CAPTURED);
        assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.CAPTURED);
        return orderId;
    }

    /**
     * 与信済みで、決済サービスでは与信が切れている支払を作る。
     *
     * @return 注文 ID
     */
    private UUID withExpiredAuthorization() {
        var orderId = payments.authorized(ORDERED_AT);
        gateway.expireAuthorization(gateway.authorizationRefOf(payments.pageIdOf(orderId)));
        return orderId;
    }

    /**
     * 売上確定が決済サービスに与信期限切れで断られ、与信期限切れになった支払を作る。
     *
     * @return 注文 ID
     */
    private UUID authorizationExpired() {
        var orderId = withExpiredAuthorization();
        assertBusinessError(() -> paymentApi.capture(orderId), PaymentErrorCode.AUTHORIZATION_EXPIRED);
        return orderId;
    }
}

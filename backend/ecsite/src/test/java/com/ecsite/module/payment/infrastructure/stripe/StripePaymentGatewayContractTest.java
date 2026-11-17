package com.ecsite.module.payment.infrastructure.stripe;

import com.ecsite.module.payment.application.port.PaymentGateway;
import com.ecsite.module.payment.application.port.PaymentGateway.CaptureRequest;
import com.ecsite.module.payment.application.port.PaymentGateway.CaptureResult;
import com.ecsite.module.payment.infrastructure.PaymentGatewayContractTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;

import static com.ecsite.module.payment.infrastructure.stripe.StripeApiStub.completedSession;
import static com.ecsite.module.payment.infrastructure.stripe.StripeApiStub.error;
import static com.ecsite.module.payment.infrastructure.stripe.StripeApiStub.intent;
import static com.ecsite.module.payment.infrastructure.stripe.StripeApiStub.session;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stripe の実装が、port の振る舞い（{@link PaymentGatewayContractTest}）を守ることの検証
 *
 * <p>状態は、手元の HTTP サーバー（{@link StripeApiStub}）が、その状態の Stripe が返す応答（sandbox で記録した形）を返して用意する。
 * 与信の期限切れは Stripe の側で起きる状態で、本番の mock は持たないので、その振る舞いはここだけで確認する。
 */
@DisplayName("Stripe の実装")
class StripePaymentGatewayContractTest extends PaymentGatewayContractTest {

    // ============================== 定数 ==============================

    /**
     * PaymentIntent の ID（与信の参照）
     */
    private static final String INTENT_ID = "pi_test_contract";

    /**
     * Checkout Session の ID（支払画面 ID）
     */
    private static final String SESSION_ID = "cs_test_contract";

    /**
     * Checkout Session の URL
     */
    private static final String SESSION_URL = "https://checkout.stripe.com/c/pay/cs_test_contract";

    /**
     * PaymentIntent の作成時刻（エポック秒）
     */
    private static final long INTENT_CREATED = 1_793_926_800L;

    // ============================== 対象 ==============================

    /**
     * Stripe の API の代わりの HTTP サーバー
     */
    private StripeApiStub stripe;

    /**
     * 確認する実装
     */
    private StripePaymentGateway gateway;

    @BeforeEach
    void setUp() throws IOException {
        stripe = new StripeApiStub();
        gateway = new StripePaymentGateway(stripe.client(), Clock.systemUTC());
    }

    @AfterEach
    void tearDown() {
        stripe.close();
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("与信の期限切れ（Stripe だけの契約）")
    class AuthorizationExpiry {

        @Test
        @DisplayName("期限の切れた与信の売上を確定すると、与信期限切れ")
        void capturesExpiredAuthorization() {
            // 準備：期限の切れた与信
            var authorizationRef = authorized();
            givenAuthorizationExpired(authorizationRef);

            // 操作：売上を確定する
            var result = gateway.capture(new CaptureRequest(ORDER_ID, authorizationRef, AMOUNT));

            // 確認：与信期限切れ
            assertThat(result).isEqualTo(CaptureResult.AUTHORIZATION_EXPIRED);
        }

        @Test
        @DisplayName("与信が切れた支払画面からも、与信を取得できる")
        void fetchesAuthorizationOfPageWithExpiredAuthorization() {
            // 準備：支払が済み、与信の期限が切れた画面
            var pageId = paidPageWithExpiredAuthorization();

            // 操作：与信を取得する
            var authorization = gateway.fetchAuthorization(pageId);

            // 確認：与信の参照と与信の時刻がある
            assertThat(authorization.authorizationRef()).isNotBlank();
            assertThat(authorization.authorizedAt()).isNotNull();
        }
    }

    // ============================== 状態の用意 ==============================

    @Override
    protected PaymentGateway gateway() {
        return gateway;
    }

    @Override
    protected String authorized() {
        stripe.respond("GET /v1/payment_intents/" + INTENT_ID, 200, intent(INTENT_ID, "requires_capture"));
        return INTENT_ID;
    }

    @Override
    protected void givenCaptured(String authorizationRef) {
        rejectAsUnexpectedState(authorizationRef);
        stripe.respond("GET /v1/payment_intents/" + authorizationRef, 200, intent(authorizationRef, "succeeded"));
    }

    @Override
    protected void givenVoided(String authorizationRef) {
        givenAuthorizationExpired(authorizationRef);
    }

    @Override
    protected void givenRefunded(String authorizationRef) {
        stripe.respond("POST /v1/refunds", 400, error("invalid_request_error", "charge_already_refunded"));
    }

    @Override
    protected String paidPage() {
        rejectExpire();
        stripe.respond("GET /v1/checkout/sessions/" + SESSION_ID, 200,
                completedSession(SESSION_ID, SESSION_URL, INTENT_ID, "requires_capture", INTENT_CREATED));
        return SESSION_ID;
    }

    @Override
    protected String closedPage() {
        rejectExpire();
        stripe.respond("GET /v1/checkout/sessions/" + SESSION_ID, 200, session(SESSION_ID, SESSION_URL, "expired"));
        return SESSION_ID;
    }

    /**
     * 与信の期限が切れた状態にする（Stripe は期限の切れた与信を取り消す）。
     *
     * @param authorizationRef 与信の参照
     */
    private void givenAuthorizationExpired(String authorizationRef) {
        rejectAsUnexpectedState(authorizationRef);
        stripe.respond("GET /v1/payment_intents/" + authorizationRef, 200, intent(authorizationRef, "canceled"));
    }

    /**
     * 支払の済んだ支払画面で、与信の期限が切れた状態にする。
     *
     * @return 支払画面 ID
     */
    private String paidPageWithExpiredAuthorization() {
        rejectExpire();
        stripe.respond("GET /v1/checkout/sessions/" + SESSION_ID, 200,
                completedSession(SESSION_ID, SESSION_URL, INTENT_ID, "canceled", INTENT_CREATED));
        return SESSION_ID;
    }

    /**
     * 今の状態ではできないリクエスト（確定済み・取消済みの PaymentIntent の売上確定と取消）を、Stripe と同じ形で断る。
     *
     * @param authorizationRef 与信の参照
     */
    private void rejectAsUnexpectedState(String authorizationRef) {
        var rejected = error("invalid_request_error", "payment_intent_unexpected_state");
        stripe.respond("POST /v1/payment_intents/" + authorizationRef + "/capture", 400, rejected);
        stripe.respond("POST /v1/payment_intents/" + authorizationRef + "/cancel", 400, rejected);
    }

    /**
     * 開いていない Session を閉じるリクエストを、Stripe と同じ形で断る。
     */
    private void rejectExpire() {
        stripe.respond("POST /v1/checkout/sessions/" + SESSION_ID + "/expire", 400,
                error("invalid_request_error", "checkout_session_not_open"));
    }
}

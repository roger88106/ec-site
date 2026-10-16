package com.ecsite.module.payment.infrastructure.stripe;

import com.ecsite.module.payment.application.port.PaymentGateway;
import com.ecsite.module.payment.infrastructure.PaymentGatewayContractTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

import java.io.IOException;
import java.time.Clock;

import static com.ecsite.module.payment.infrastructure.stripe.StripeApiStub.error;
import static com.ecsite.module.payment.infrastructure.stripe.StripeApiStub.intent;
import static com.ecsite.module.payment.infrastructure.stripe.StripeApiStub.session;

/**
 * Stripe の実装が、port の振る舞い（{@link PaymentGatewayContractTest}）を守ることの検証。<br />
 * 状態は、手元の HTTP サーバー（{@link StripeApiStub}）が、その状態の Stripe が返す応答（sandbox で記録した形）を返して用意する。
 */
@DisplayName("Stripe の実装")
class StripePaymentGatewayContractTest extends PaymentGatewayContractTest {

    private static final String INTENT_ID = "pi_test_contract";

    private static final String SESSION_ID = "cs_test_contract";

    private static final String SESSION_URL = "https://checkout.stripe.com/c/pay/cs_test_contract";

    private StripeApiStub stripe;

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
    protected void givenAuthorizationExpired(String authorizationRef) {
        rejectAsUnexpectedState(authorizationRef);
        stripe.respond("GET /v1/payment_intents/" + authorizationRef, 200, intent(authorizationRef, "canceled"));
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
        stripe.respond("GET /v1/checkout/sessions/" + SESSION_ID, 200, session(SESSION_ID, SESSION_URL, "complete"));
        return SESSION_ID;
    }

    @Override
    protected String closedPage() {
        rejectExpire();
        stripe.respond("GET /v1/checkout/sessions/" + SESSION_ID, 200, session(SESSION_ID, SESSION_URL, "expired"));
        return SESSION_ID;
    }

    /**
     * 今の状態ではできない要求（確定済み・取消済みの PaymentIntent の売上確定と取消）を、Stripe と同じ形で断る。
     */
    private void rejectAsUnexpectedState(String authorizationRef) {
        var rejected = error("invalid_request_error", "payment_intent_unexpected_state");
        stripe.respond("POST /v1/payment_intents/" + authorizationRef + "/capture", 400, rejected);
        stripe.respond("POST /v1/payment_intents/" + authorizationRef + "/cancel", 400, rejected);
    }

    /**
     * 開いていない Session を閉じる要求を、Stripe と同じ形で断る。
     */
    private void rejectExpire() {
        stripe.respond("POST /v1/checkout/sessions/" + SESSION_ID + "/expire", 400,
                error("invalid_request_error", "checkout_session_not_open"));
    }
}

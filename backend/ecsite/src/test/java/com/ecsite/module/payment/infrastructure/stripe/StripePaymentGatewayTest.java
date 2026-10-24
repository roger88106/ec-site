package com.ecsite.module.payment.infrastructure.stripe;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.ErrorCode;
import com.ecsite.common.error.exception.SystemException;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.application.PaymentProperties;
import com.ecsite.module.payment.application.port.PaymentGateway.CaptureRequest;
import com.ecsite.module.payment.application.port.PaymentGateway.CaptureResult;
import com.ecsite.module.payment.application.port.PaymentGateway.CloseResult;
import com.ecsite.module.payment.application.port.PaymentGateway.PageRequest;
import com.ecsite.module.payment.application.port.PaymentGateway.RefundRequest;
import com.ecsite.module.payment.domain.PaymentErrorCode;
import com.ecsite.module.payment.domain.payment.PaymentPage;
import com.ecsite.module.payment.infrastructure.stripe.StripeApiStub.Request;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.hibernate.validator.constraints.time.DurationMax;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stripe の実装の単体テスト（docs/design/modules/payment.md「Stripe」）。支払画面の作成・閉じる、与信の取消、売上確定、返金。<br />
 * Stripe の API の代わりに手元の HTTP サーバー（{@link StripeApiStub}）を立て、sandbox で記録した応答（T9501）の形を返す。
 */
class StripePaymentGatewayTest {

    private static final UUID ORDER_ID = UUID.fromString("0c4b7f7e-2f2c-4a59-9a39-8a0b6a0f0001");

    private static final String INTENT_ID = "pi_test_capture";

    private static final UUID PAYMENT_ID = UUID.fromString("0c4b7f7e-2f2c-4a59-9a39-8a0b6a0f0002");

    private static final String SESSION_ID = "cs_test_page";

    private static final String SESSION_URL = "https://checkout.stripe.com/c/pay/cs_test_page";

    private static final String RETURN_URL = "http://localhost:3000/orders/" + ORDER_ID + "?payment=returned";

    /**
     * テストの今の時刻（支払画面の期限の基準）
     */
    private static final Instant NOW = Instant.parse("2026-10-14T01:00:00Z");

    private StripeApiStub stripe;

    private StripePaymentGateway gateway;

    @BeforeEach
    void setUp() throws IOException {
        stripe = new StripeApiStub();
        gateway = new StripePaymentGateway(stripe.client(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        stripe.close();
    }

    @Nested
    @DisplayName("売上確定")
    class Capture {

        @Test
        @DisplayName("PaymentIntent を総額で確定し、成功なら確定済み。冪等キーは注文 ID から作る")
        void captures() {
            respond("POST /v1/payment_intents/" + INTENT_ID + "/capture", 200, intent("succeeded"));

            assertThat(gateway.capture(captureRequest())).isEqualTo(CaptureResult.CAPTURED);

            var request = requests().getFirst();
            assertThat(request.body()).contains("amount_to_capture=6940");
            assertThat(request.idempotencyKey()).isEqualTo("capture-" + ORDER_ID);
        }

        @Test
        @DisplayName("確定済みで断られたら（状態 succeeded）、成功とする")
        void alreadyCaptured() {
            respond("POST /v1/payment_intents/" + INTENT_ID + "/capture", 400,
                    StripeApiStub.error("invalid_request_error", "payment_intent_unexpected_state"));
            respond("GET /v1/payment_intents/" + INTENT_ID, 200, intent("succeeded"));

            assertThat(gateway.capture(captureRequest())).isEqualTo(CaptureResult.CAPTURED);
        }

        @Test
        @DisplayName("与信が切れて断られたら（状態 canceled）、与信期限切れ")
        void authorizationExpired() {
            respond("POST /v1/payment_intents/" + INTENT_ID + "/capture", 400,
                    StripeApiStub.error("invalid_request_error", "payment_intent_unexpected_state"));
            respond("GET /v1/payment_intents/" + INTENT_ID, 200, intent("canceled"));

            assertThat(gateway.capture(captureRequest())).isEqualTo(CaptureResult.AUTHORIZATION_EXPIRED);
        }

        @Test
        @DisplayName("Stripe の 5xx は、接続できない")
        void serverError() {
            respond("POST /v1/payment_intents/" + INTENT_ID + "/capture", 500, StripeApiStub.error("api_error", null));

            assertThat(gateway.capture(captureRequest())).isEqualTo(CaptureResult.UNREACHABLE);
        }

        @Test
        @DisplayName("Stripe に接続できなければ、接続できない")
        void unreachable() throws IOException {
            var unreachable = new StripePaymentGateway(StripeApiStub.client("http://localhost:" + StripeApiStub.closedPort()), Clock.systemUTC());

            assertThat(unreachable.capture(captureRequest())).isEqualTo(CaptureResult.UNREACHABLE);
        }

        @Test
        @DisplayName("他の拒否は、システムのエラー")
        void otherRejection() {
            respond("POST /v1/payment_intents/" + INTENT_ID + "/capture", 400,
                    StripeApiStub.error("invalid_request_error", "amount_too_large"));

            assertThatThrownBy(() -> gateway.capture(captureRequest())).isInstanceOf(SystemException.class);
        }
    }

    @Nested
    @DisplayName("返金")
    class Refund {

        @Test
        @DisplayName("PaymentIntent の総額を返金する。冪等キーは注文 ID から作る")
        void refunds() {
            respond("POST /v1/refunds", 200, """
                    {"id": "re_test", "object": "refund", "status": "succeeded", "amount": 6940}""");

            gateway.refund(refundRequest());

            var request = requests().getFirst();
            assertThat(request.body()).contains("payment_intent=" + INTENT_ID, "amount=6940");
            assertThat(request.idempotencyKey()).isEqualTo("refund-" + ORDER_ID);
        }

        @Test
        @DisplayName("返金済みで断られたら（charge_already_refunded）、成功とする")
        void alreadyRefunded() {
            respond("POST /v1/refunds", 400, StripeApiStub.error("invalid_request_error", "charge_already_refunded"));

            gateway.refund(refundRequest());

            assertThat(requests()).hasSize(1);
        }

        @Test
        @DisplayName("他の失敗は、システムのエラー（再発行でやり直す）")
        void failure() {
            respond("POST /v1/refunds", 500, StripeApiStub.error("api_error", null));

            assertThatThrownBy(() -> gateway.refund(refundRequest())).isInstanceOf(SystemException.class);
        }
    }

    @Nested
    @DisplayName("支払画面の作成")
    class OpenPage {

        @Test
        @DisplayName("与信だけを取る Checkout Session を、総額・戻り先・31 分の期限で作り、画面の ID と URL を返す")
        void opensAuthorizationOnlySession() {
            respond("POST /v1/checkout/sessions", 200, session("open"));

            var page = gateway.openPage(new PageRequest(PAYMENT_ID, ORDER_ID, Money.of(6940), RETURN_URL));

            assertThat(page).isEqualTo(new PaymentPage(SESSION_ID, SESSION_URL));
            var body = requests().getFirst().body();
            assertThat(body).contains(
                    "mode=payment",
                    "payment_intent_data[capture_method]=manual",
                    "line_items[0][price_data][unit_amount]=6940",
                    "line_items[0][price_data][currency]=jpy",
                    "line_items[0][quantity]=1",
                    "success_url=" + RETURN_URL,
                    "cancel_url=" + RETURN_URL,
                    "client_reference_id=" + ORDER_ID,
                    "metadata[paymentId]=" + PAYMENT_ID,
                    "payment_intent_data[metadata][orderId]=" + ORDER_ID,
                    "expires_at=" + NOW.plus(Duration.ofMinutes(31)).getEpochSecond());
        }

        @Test
        @DisplayName("支払画面の期限は、支払の期限の上限（deadline の @DurationMax）より長い")
        void pageLifetimeExceedsDeadlineLimit() throws NoSuchFieldException {
            respond("POST /v1/checkout/sessions", 200, session("open"));

            gateway.openPage(new PageRequest(PAYMENT_ID, ORDER_ID, Money.of(6940), RETURN_URL));

            // 期限の定数は private で、テストのために広げない。Stripe に渡した expires_at から期限を求める。
            // 上限はテストに値を写さず、設定の注釈から読む（上限を延ばしたときに、このテストが失敗して気づく）
            var matcher = Pattern.compile("expires_at=(\\d+)").matcher(requests().getFirst().body());
            assertThat(matcher.find()).isTrue();
            var pageLifetime = Duration.between(NOW, Instant.ofEpochSecond(Long.parseLong(matcher.group(1))));
            var limit = PaymentProperties.class.getDeclaredField("deadline").getAnnotation(DurationMax.class);
            var deadlineLimit = Duration.ofDays(limit.days()).plusHours(limit.hours()).plusMinutes(limit.minutes())
                    .plusSeconds(limit.seconds()).plusMillis(limit.millis()).plusNanos(limit.nanos());
            assertThat(pageLifetime).isGreaterThan(deadlineLimit);
        }

        @Test
        @DisplayName("作れなければ、システムのエラー")
        void failure() {
            respond("POST /v1/checkout/sessions", 500, StripeApiStub.error("api_error", null));

            assertThatThrownBy(() -> gateway.openPage(new PageRequest(PAYMENT_ID, ORDER_ID, Money.of(6940), RETURN_URL)))
                    .isInstanceOf(SystemException.class);
        }
    }

    @Nested
    @DisplayName("支払画面を閉じる")
    class ClosePage {

        @Test
        @DisplayName("開いている Session を期限切れにし、閉じた")
        void closes() {
            respond("POST /v1/checkout/sessions/" + SESSION_ID + "/expire", 200, session("expired"));

            assertThat(gateway.closePage(SESSION_ID)).isEqualTo(CloseResult.CLOSED);
        }

        @Test
        @DisplayName("閉じられず、読み直すと支払が済んでいたら（complete）、支払済み")
        void alreadyPaid() {
            respond("POST /v1/checkout/sessions/" + SESSION_ID + "/expire", 400,
                    StripeApiStub.error("invalid_request_error", "checkout_session_not_open"));
            respond("GET /v1/checkout/sessions/" + SESSION_ID, 200, session("complete"));

            assertThat(gateway.closePage(SESSION_ID)).isEqualTo(CloseResult.ALREADY_PAID);
        }

        @Test
        @DisplayName("閉じられず、読み直すと閉じていたら（expired）、閉じた")
        void alreadyClosed() {
            respond("POST /v1/checkout/sessions/" + SESSION_ID + "/expire", 400,
                    StripeApiStub.error("invalid_request_error", "checkout_session_not_open"));
            respond("GET /v1/checkout/sessions/" + SESSION_ID, 200, session("expired"));

            assertThat(gateway.closePage(SESSION_ID)).isEqualTo(CloseResult.CLOSED);
        }

        @Test
        @DisplayName("Stripe の 5xx は、システムのエラー")
        void failure() {
            respond("POST /v1/checkout/sessions/" + SESSION_ID + "/expire", 500, StripeApiStub.error("api_error", null));

            assertThatThrownBy(() -> gateway.closePage(SESSION_ID)).isInstanceOf(SystemException.class);
        }
    }

    @Nested
    @DisplayName("与信の取消")
    class VoidAuthorization {

        @Test
        @DisplayName("PaymentIntent を取り消す")
        void voids() {
            respond("POST /v1/payment_intents/" + INTENT_ID + "/cancel", 200, intent("canceled"));

            gateway.voidAuthorization(INTENT_ID);

            assertThat(requests()).extracting(Request::key).containsExactly("POST /v1/payment_intents/" + INTENT_ID + "/cancel");
        }

        @Test
        @DisplayName("取消済みで断られたら（状態 canceled）、成功とする")
        void alreadyVoided() {
            respond("POST /v1/payment_intents/" + INTENT_ID + "/cancel", 400,
                    StripeApiStub.error("invalid_request_error", "payment_intent_unexpected_state"));
            respond("GET /v1/payment_intents/" + INTENT_ID, 200, intent("canceled"));

            gateway.voidAuthorization(INTENT_ID);
        }

        @Test
        @DisplayName("断られ、取消済みでなければ（売上確定済みなど）、システムのエラー")
        void rejected() {
            respond("POST /v1/payment_intents/" + INTENT_ID + "/cancel", 400,
                    StripeApiStub.error("invalid_request_error", "payment_intent_unexpected_state"));
            respond("GET /v1/payment_intents/" + INTENT_ID, 200, intent("succeeded"));

            assertThatThrownBy(() -> gateway.voidAuthorization(INTENT_ID)).isInstanceOf(SystemException.class);
        }

        @Test
        @DisplayName("Stripe の 5xx は、システムのエラー（再発行でやり直す）")
        void failure() {
            respond("POST /v1/payment_intents/" + INTENT_ID + "/cancel", 500, StripeApiStub.error("api_error", null));

            assertThatThrownBy(() -> gateway.voidAuthorization(INTENT_ID)).isInstanceOf(SystemException.class);
        }
    }

    @Nested
    @DisplayName("失敗の分類")
    class FailureClassification {

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.ecsite.module.payment.infrastructure.stripe.StripePaymentGatewayTest#operations")
        @DisplayName("AC-294 Stripe の側の故障（5xx）は、外部サービスのエラー")
        void serverErrorIsUnavailable(String name, String key, Consumer<StripePaymentGateway> operation) {
            respond(key, 500, StripeApiStub.error("api_error", null));

            assertFailure(() -> operation.accept(gateway), PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE.getCode());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.ecsite.module.payment.infrastructure.stripe.StripePaymentGatewayTest#operations")
        @DisplayName("AC-294 回数の制限（429）は、外部サービスのエラー")
        void rateLimitIsUnavailable(String name, String key, Consumer<StripePaymentGateway> operation) {
            respond(key, 429, StripeApiStub.error("invalid_request_error", "rate_limit"));

            assertFailure(() -> operation.accept(gateway), PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE.getCode());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.ecsite.module.payment.infrastructure.stripe.StripePaymentGatewayTest#operations")
        @DisplayName("AC-294 認証の誤り（401）は、予期しないエラー（設定の誤り）")
        void authenticationErrorIsSystemError(String name, String key, Consumer<StripePaymentGateway> operation) {
            respond(key, 401, StripeApiStub.error("invalid_request_error", null));

            assertFailure(() -> operation.accept(gateway), CommonErrorCode.SYSTEM_ERROR.getCode());
        }

        @Test
        @DisplayName("AC-295 Stripe に接続できなければ、支払画面を作る・閉じる、返金、取消は外部サービスのエラー")
        void unreachableIsUnavailable() throws IOException {
            var unreachable = new StripePaymentGateway(
                    StripeApiStub.client("http://localhost:" + StripeApiStub.closedPort()), Clock.fixed(NOW, ZoneOffset.UTC));
            var code = PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE.getCode();

            assertFailure(() -> unreachable.openPage(pageRequest()), code);
            assertFailure(() -> unreachable.closePage(SESSION_ID), code);
            assertFailure(() -> unreachable.refund(refundRequest()), code);
            assertFailure(() -> unreachable.voidAuthorization(INTENT_ID), code);
        }

        @Test
        @DisplayName("AC-294 状態の読み取り（閉じられなかった画面の読み直し）で Stripe の側が故障していれば、外部サービスのエラー")
        void retrieveServerErrorIsUnavailable() {
            respond("POST /v1/checkout/sessions/" + SESSION_ID + "/expire", 400,
                    StripeApiStub.error("invalid_request_error", "checkout_session_not_open"));
            respond("GET /v1/checkout/sessions/" + SESSION_ID, 503, StripeApiStub.error("api_error", null));

            assertFailure(() -> gateway.closePage(SESSION_ID), PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE.getCode());
        }

        @Test
        @DisplayName("売上確定の回数の制限は、今の接続できないと同じく、接続できないという結果を返す")
        void captureRateLimitIsUnreachable() {
            respond("POST /v1/payment_intents/" + INTENT_ID + "/capture", 429,
                    StripeApiStub.error("invalid_request_error", "rate_limit"));

            assertThat(gateway.capture(captureRequest())).isEqualTo(CaptureResult.UNREACHABLE);
        }

        private void assertFailure(ThrowingCallable call, ErrorCode errorCode) {
            assertThatThrownBy(call).isInstanceOfSatisfying(SystemException.class,
                    e -> assertThat(e.getErrorDetail().errorCode()).isEqualTo(errorCode));
        }
    }

    /**
     * 失敗の分類を確認する操作（名前、Stripe の要求、呼び出し）。売上確定は結果で返すので除く
     */
    static Stream<Arguments> operations() {
        return Stream.of(
                Arguments.of("支払画面を作る", "POST /v1/checkout/sessions",
                        (Consumer<StripePaymentGateway>) gateway -> gateway.openPage(pageRequest())),
                Arguments.of("支払画面を閉じる", "POST /v1/checkout/sessions/" + SESSION_ID + "/expire",
                        (Consumer<StripePaymentGateway>) gateway -> gateway.closePage(SESSION_ID)),
                Arguments.of("返金", "POST /v1/refunds",
                        (Consumer<StripePaymentGateway>) gateway -> gateway.refund(refundRequest())),
                Arguments.of("与信の取消", "POST /v1/payment_intents/" + INTENT_ID + "/cancel",
                        (Consumer<StripePaymentGateway>) gateway -> gateway.voidAuthorization(INTENT_ID)));
    }

    // ============================== 補助 ==============================

    private static PageRequest pageRequest() {
        return new PageRequest(PAYMENT_ID, ORDER_ID, Money.of(6940), RETURN_URL);
    }

    private static String session(String status) {
        return StripeApiStub.session(SESSION_ID, SESSION_URL, status);
    }

    private static CaptureRequest captureRequest() {
        return new CaptureRequest(ORDER_ID, INTENT_ID, Money.of(6940));
    }

    private static RefundRequest refundRequest() {
        return new RefundRequest(ORDER_ID, INTENT_ID, Money.of(6940));
    }

    private void respond(String key, int status, String body) {
        stripe.respond(key, status, body);
    }

    private List<Request> requests() {
        return stripe.requests();
    }

    private static String intent(String status) {
        return StripeApiStub.intent(INTENT_ID, status);
    }
}

package com.ecsite.module.payment.infrastructure.stripe;

import com.ecsite.common.error.exception.SystemException;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.application.port.PaymentGateway.CaptureRequest;
import com.ecsite.module.payment.application.port.PaymentGateway.CaptureResult;
import com.ecsite.module.payment.application.port.PaymentGateway.RefundRequest;
import com.stripe.StripeClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stripe の売上確定と返金の単体テスト（T9502、docs/backend/modules/payment.md「Stripe」）。<br />
 * Stripe の API の代わりに手元の HTTP サーバーを立て、sandbox で記録した応答（T9501）を返す。
 */
class StripePaymentGatewayTest {

    private static final UUID ORDER_ID = UUID.fromString("0c4b7f7e-2f2c-4a59-9a39-8a0b6a0f0001");

    private static final String INTENT_ID = "pi_test_capture";

    /**
     * パス（メソッドとパス）→ 返す応答
     */
    private final Map<String, Response> responses = new HashMap<>();

    /**
     * 受けた要求
     */
    private final List<Request> requests = new ArrayList<>();

    private HttpServer server;

    private StripePaymentGateway gateway;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            var key = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
            var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new Request(key, URLDecoder.decode(body, StandardCharsets.UTF_8),
                    exchange.getRequestHeaders().getFirst("Idempotency-Key")));
            var response = responses.getOrDefault(key, new Response(404, error("invalid_request_error", "resource_missing")));
            var bytes = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(response.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        gateway = new StripePaymentGateway(client("http://localhost:" + server.getAddress().getPort()), Clock.systemUTC());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Nested
    @DisplayName("売上確定")
    class Capture {

        @Test
        @DisplayName("PaymentIntent を総額で確定し、成功なら確定済み。冪等キーは注文 ID から作る")
        void captures() {
            respond("POST /v1/payment_intents/" + INTENT_ID + "/capture", 200, intent("succeeded"));

            assertThat(gateway.capture(captureRequest())).isEqualTo(CaptureResult.CAPTURED);

            var request = requests.getFirst();
            assertThat(request.body()).contains("amount_to_capture=6940");
            assertThat(request.idempotencyKey()).isEqualTo("capture-" + ORDER_ID);
        }

        @Test
        @DisplayName("確定済みで断られたら（状態 succeeded）、成功とする")
        void alreadyCaptured() {
            respond("POST /v1/payment_intents/" + INTENT_ID + "/capture", 400,
                    error("invalid_request_error", "payment_intent_unexpected_state"));
            respond("GET /v1/payment_intents/" + INTENT_ID, 200, intent("succeeded"));

            assertThat(gateway.capture(captureRequest())).isEqualTo(CaptureResult.CAPTURED);
        }

        @Test
        @DisplayName("与信が切れて断られたら（状態 canceled）、与信期限切れ")
        void authorizationExpired() {
            respond("POST /v1/payment_intents/" + INTENT_ID + "/capture", 400,
                    error("invalid_request_error", "payment_intent_unexpected_state"));
            respond("GET /v1/payment_intents/" + INTENT_ID, 200, intent("canceled"));

            assertThat(gateway.capture(captureRequest())).isEqualTo(CaptureResult.AUTHORIZATION_EXPIRED);
        }

        @Test
        @DisplayName("Stripe の 5xx は、接続できない")
        void serverError() {
            respond("POST /v1/payment_intents/" + INTENT_ID + "/capture", 500, error("api_error", null));

            assertThat(gateway.capture(captureRequest())).isEqualTo(CaptureResult.UNREACHABLE);
        }

        @Test
        @DisplayName("Stripe に接続できなければ、接続できない")
        void unreachable() throws IOException {
            var unreachable = new StripePaymentGateway(client("http://localhost:" + closedPort()), Clock.systemUTC());

            assertThat(unreachable.capture(captureRequest())).isEqualTo(CaptureResult.UNREACHABLE);
        }

        @Test
        @DisplayName("他の拒否は、システムのエラー")
        void otherRejection() {
            respond("POST /v1/payment_intents/" + INTENT_ID + "/capture", 400,
                    error("invalid_request_error", "amount_too_large"));

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

            var request = requests.getFirst();
            assertThat(request.body()).contains("payment_intent=" + INTENT_ID, "amount=6940");
            assertThat(request.idempotencyKey()).isEqualTo("refund-" + ORDER_ID);
        }

        @Test
        @DisplayName("返金済みで断られたら（charge_already_refunded）、成功とする")
        void alreadyRefunded() {
            respond("POST /v1/refunds", 400, error("invalid_request_error", "charge_already_refunded"));

            gateway.refund(refundRequest());

            assertThat(requests).hasSize(1);
        }

        @Test
        @DisplayName("他の失敗は、システムのエラー（再発行でやり直す）")
        void failure() {
            respond("POST /v1/refunds", 500, error("api_error", null));

            assertThatThrownBy(() -> gateway.refund(refundRequest())).isInstanceOf(SystemException.class);
        }
    }

    // ============================== 補助 ==============================

    private static CaptureRequest captureRequest() {
        return new CaptureRequest(ORDER_ID, INTENT_ID, Money.of(6940));
    }

    private static RefundRequest refundRequest() {
        return new RefundRequest(ORDER_ID, INTENT_ID, Money.of(6940));
    }

    private void respond(String key, int status, String body) {
        responses.put(key, new Response(status, body));
    }

    /**
     * 手元のサーバーを API の宛先にした Stripe の API（やり直しはしない）
     */
    private static StripeClient client(String apiBase) {
        return StripeClient.builder().setApiKey("sk_test_unit").setApiBase(apiBase).setMaxNetworkRetries(0).build();
    }

    /**
     * 使われていないポート（つないでも誰も受けない）
     */
    private static int closedPort() throws IOException {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /**
     * sandbox で記録した PaymentIntent の応答（要る項目だけ）
     */
    private static String intent(String status) {
        return """
                {"id": "%s", "object": "payment_intent", "amount": 6940, "currency": "jpy", "status": "%s"}"""
                .formatted(INTENT_ID, status);
    }

    /**
     * sandbox で記録したエラーの応答（要る項目だけ）
     */
    private static String error(String type, String code) {
        return code == null
                ? """
                {"error": {"type": "%s", "message": "error"}}""".formatted(type)
                : """
                {"error": {"type": "%s", "code": "%s", "message": "error"}}""".formatted(type, code);
    }

    private record Response(int status, String body) {
    }

    private record Request(String key, String body, String idempotencyKey) {
    }
}

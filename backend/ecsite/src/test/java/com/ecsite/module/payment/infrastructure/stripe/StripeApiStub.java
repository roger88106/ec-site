package com.ecsite.module.payment.infrastructure.stripe;

import com.stripe.StripeClient;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stripe の API の代わりに立てる、手元の HTTP サーバー。sandbox で記録した応答の形（要る項目だけ）を返す。<br />
 * 返す応答はテストが決め、決めていない要求には 404（resource_missing）を返す。
 */
final class StripeApiStub implements AutoCloseable {

    /**
     * パス（メソッドとパス）→ 返す応答
     */
    private final Map<String, Response> responses = new ConcurrentHashMap<>();

    /**
     * 受けた要求（受けた順）
     */
    private final List<Request> requests = new CopyOnWriteArrayList<>();

    /**
     * 手元の HTTP サーバー
     */
    private final HttpServer server;

    /**
     * 空いているポートでサーバーを立てる。
     *
     * @throws IOException サーバーを立てられない
     */
    StripeApiStub() throws IOException {
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
    }

    /**
     * 要求に返す応答を決める。
     *
     * @param key    メソッドとパス（例：{@code POST /v1/refunds}）
     * @param status HTTP の状態
     * @param body   本文
     */
    void respond(String key, int status, String body) {
        responses.put(key, new Response(status, body));
    }

    /**
     * @return 受けた要求（受けた順）
     */
    List<Request> requests() {
        return requests;
    }

    /**
     * @return このサーバーを API の宛先にした Stripe の API
     */
    StripeClient client() {
        return client("http://localhost:" + server.getAddress().getPort());
    }

    @Override
    public void close() {
        server.stop(0);
    }

    /**
     * 宛先を指定した Stripe の API（やり直しはしない）。
     *
     * @param apiBase API の宛先
     * @return Stripe の API
     */
    static StripeClient client(String apiBase) {
        return StripeClient.builder().setApiKey("sk_test_unit").setApiBase(apiBase).setMaxNetworkRetries(0).build();
    }

    /**
     * @return 使われていないポート（つないでも誰も受けない）
     * @throws IOException ポートを探せない
     */
    static int closedPort() throws IOException {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /**
     * PaymentIntent の応答。
     *
     * @param id     PaymentIntent の ID
     * @param status 状態
     * @return 本文
     */
    static String intent(String id, String status) {
        return """
                {"id": "%s", "object": "payment_intent", "amount": 6940, "currency": "jpy", "status": "%s"}"""
                .formatted(id, status);
    }

    /**
     * Checkout Session の応答。
     *
     * @param id     Session の ID
     * @param url    支払の画面の URL
     * @param status 状態
     * @return 本文
     */
    static String session(String id, String url, String status) {
        return """
                {"id": "%s", "object": "checkout.session", "url": "%s", "status": "%s"}"""
                .formatted(id, url, status);
    }

    /**
     * エラーの応答。
     *
     * @param type 種類
     * @param code コード。なければ null
     * @return 本文
     */
    static String error(String type, String code) {
        return code == null
                ? """
                {"error": {"type": "%s", "message": "error"}}""".formatted(type)
                : """
                {"error": {"type": "%s", "code": "%s", "message": "error"}}""".formatted(type, code);
    }

    /**
     * 返す応答
     *
     * @param status HTTP の状態
     * @param body   本文
     */
    private record Response(int status, String body) {
    }

    /**
     * 受けた要求
     *
     * @param key            メソッドとパス
     * @param body           本文（URL のデコードの後）
     * @param idempotencyKey 冪等キーのヘッダー
     */
    record Request(String key, String body, String idempotencyKey) {
    }
}

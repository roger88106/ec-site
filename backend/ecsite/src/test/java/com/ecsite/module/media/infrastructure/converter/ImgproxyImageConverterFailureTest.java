package com.ecsite.module.media.infrastructure.converter;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.error.exception.SystemException;
import com.ecsite.module.media.infrastructure.storage.MediaStorageProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 画像の変換の imgproxy の実装の、失敗のときの振る舞いのテスト（docs/modules/media.md「アップロードの流れ」）。<br />
 * imgproxy の代わりに、テストの中の小さな HTTP サーバーが応答する。
 * imgproxy が変換を断った・時間の上限を超えたときは業務のエラー（変換の失敗）、imgproxy につながらないときはシステムのエラーになる。
 */
class ImgproxyImageConverterFailureTest {

    /**
     * 変換を待つ時間の上限（テストでは短くする）
     */
    private static final Duration TIMEOUT = Duration.ofMillis(300);

    /**
     * imgproxy の代わりの HTTP サーバー
     */
    private HttpServer server;

    /**
     * サーバーの応答の仕方（テストごとに決める）
     */
    private final AtomicReference<Responder> responder = new AtomicReference<>();

    /**
     * 応答しないサーバーを、テストの後に放すための合図
     */
    private final CountDownLatch release = new CountDownLatch(1);

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> responder.get().respond(exchange));
        server.start();
    }

    @AfterEach
    void stopServer() {
        release.countDown();
        server.stop(0);
    }

    @Test
    @DisplayName("imgproxy がサーバーのエラー（5xx）を返したら、変換の失敗になる")
    void failsOnServerError() {
        responder.set(exchange -> send(exchange, 500, "internal error"));

        assertConversionFailed(converter(baseUrl()));
    }

    @Test
    @DisplayName("imgproxy が要求を断った（4xx）ら、変換の失敗になる")
    void failsOnClientError() {
        responder.set(exchange -> send(exchange, 422, "unprocessable"));

        assertConversionFailed(converter(baseUrl()));
    }

    @Test
    @DisplayName("imgproxy の応答が時間の上限を超えたら、変換の失敗になる")
    void failsOnTimeout() {
        responder.set(exchange -> {
            // テストが終わるまで応答しない
            awaitRelease();
            send(exchange, 200, "late");
        });

        assertConversionFailed(converter(baseUrl()));
    }

    @Test
    @DisplayName("imgproxy につながらなければ、システムのエラーになる")
    void failsWhenUnreachable() throws IOException {
        URI unreachable;
        try (var socket = new ServerSocket(0)) {
            // 使っていないポート（閉じた後は、だれも待ち受けていない）
            unreachable = URI.create("http://127.0.0.1:" + socket.getLocalPort());
        }

        assertThatThrownBy(() -> converter(unreachable).convertToWebp("scope/upload"))
                .isInstanceOf(SystemException.class);
    }

    // ============================== 補助 ==============================

    /**
     * サーバーの応答の仕方
     */
    @FunctionalInterface
    private interface Responder {
        /**
         * 要求に応答する
         *
         * @param exchange 要求と応答
         * @throws IOException 応答を書けない
         */
        void respond(HttpExchange exchange) throws IOException;
    }

    /**
     * テストのサーバーを呼ぶ変換の実装を作る
     *
     * @param baseUrl imgproxy の URL
     * @return 変換の実装
     */
    private static ImgproxyImageConverter converter(URI baseUrl) {
        var properties = new ImgproxyProperties(baseUrl, "0123456789abcdef", "fedcba9876543210", 82, TIMEOUT);
        var storage = new MediaStorageProperties(null, "us-east-1", true, null, null,
                "incoming", "public", URI.create("http://cdn.test"), Duration.ofMinutes(5));
        return new ImgproxyImageConverter(RestClient.builder(), properties, storage);
    }

    /**
     * 変換が、業務のエラー（変換の失敗）になることを確かめる
     *
     * @param converter 変換の実装
     */
    private static void assertConversionFailed(ImgproxyImageConverter converter) {
        assertThatThrownBy(() -> converter.convertToWebp("scope/upload"))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorDetail().errorCode().code()).isEqualTo("IMAGE_CONVERSION_FAILED"));
    }

    /**
     * テストのサーバーの URL
     *
     * @return URL
     */
    private URI baseUrl() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    /**
     * テストが終わるまで待つ
     */
    private void awaitRelease() {
        try {
            release.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 応答を書く
     *
     * @param exchange 要求と応答
     * @param status   HTTP の状態
     * @param body     本文
     * @throws IOException 応答を書けない
     */
    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}

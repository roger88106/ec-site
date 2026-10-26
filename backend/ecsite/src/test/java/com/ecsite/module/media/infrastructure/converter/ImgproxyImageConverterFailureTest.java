package com.ecsite.module.media.infrastructure.converter;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.error.exception.SystemException;
import com.ecsite.module.media.infrastructure.storage.S3ObjectStorage;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 画像の変換の imgproxy の実装の、失敗のときの振る舞いのテスト（docs/design/modules/media.md「アップロードの流れ」）。<br />
 * imgproxy の代わりに、テストの中の小さな HTTP サーバーが応答する。
 * imgproxy が応答した（4xx と 500）ときは業務のエラー（変換の失敗）、imgproxy に届かない・時間の上限を超えたときは
 * 外部サービスのエラーになる。
 */
class ImgproxyImageConverterFailureTest {

    /**
     * 正常に応答するサーバーを使うテストの、変換を待つ時間の上限。
     * 負荷のある環境で応答が遅れても、時間の上限を超えたと見なされないよう長くする。
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    /**
     * 時間の上限を確かめるテストの、変換を待つ時間の上限（応答しないサーバーを待つので短くする）
     */
    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(300);

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
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 0), 0);
        server.createContext("/", exchange -> responder.get().respond(exchange));
        server.start();
    }

    @AfterEach
    void stopServer() {
        release.countDown();
        server.stop(0);
    }

    @Test
    @DisplayName("AC-297 imgproxy が 500 を返したら（壊れたファイルに 500 を返す）、変換の失敗になる")
    void failsOnInternalError() {
        responder.set(exchange -> send(exchange, 500, "Internal error"));

        assertConversionFailed(converter(baseUrl()));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(ints = {502, 503, 504})
    @DisplayName("AC-296 imgproxy に届かない（手前の代理が 502・503・504 を返す）なら、外部サービスのエラーになる")
    void unavailableOnGatewayError(int status) {
        responder.set(exchange -> send(exchange, status, "unavailable"));

        assertServiceUnavailable(converter(baseUrl()));
    }

    @Test
    @DisplayName("AC-68 imgproxy が 200 で WebP でない本文を返したら、Content-Type が WebP でも変換の失敗になる")
    void failsWhenBodyIsNotWebp() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 0, 'I', 'H', 'D', 'R'};
        responder.set(exchange -> sendBytes(exchange, "image/webp", png));

        assertConversionFailed(converter(baseUrl()));
    }

    @Test
    @DisplayName("imgproxy が 200 で WebP の本文を返したら、その本文を変換の結果にする")
    void returnsWebpBody() {
        byte[] webp = {'R', 'I', 'F', 'F', 4, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};
        responder.set(exchange -> sendBytes(exchange, "image/webp", webp));

        assertThat(converter(baseUrl()).convertToWebp("scope/upload")).isEqualTo(webp);
    }

    @Test
    @DisplayName("imgproxy には、アップロードのバケットのファイルを指す署名付きの URL で要求する")
    void requestsSignedUrlOfUploadedFile() throws Exception {
        byte[] webp = {'R', 'I', 'F', 'F', 4, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};
        var requested = new AtomicReference<String>();
        responder.set(exchange -> {
            requested.set(exchange.getRequestURI().getRawPath());
            sendBytes(exchange, "image/webp", webp);
        });

        converter(baseUrl()).convertToWebp("scope/upload");

        var source = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("s3://incoming/scope/upload".getBytes(StandardCharsets.UTF_8));
        var path = "/q:82/f:webp/" + source;
        var hex = HexFormat.of();
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(hex.parseHex("0123456789abcdef"), "HmacSHA256"));
        mac.update(hex.parseHex("fedcba9876543210"));
        mac.update(path.getBytes(StandardCharsets.UTF_8));
        var signature = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal());
        assertThat(requested.get()).isEqualTo("/" + signature + path);
    }

    @Test
    @DisplayName("AC-297 imgproxy が要求を断った（4xx）ら、変換の失敗になる")
    void failsOnClientError() {
        responder.set(exchange -> send(exchange, 422, "unprocessable"));

        assertConversionFailed(converter(baseUrl()));
    }

    @Test
    @DisplayName("AC-296 imgproxy の応答が時間の上限を超えたら、外部サービスのエラーになる")
    void unavailableOnTimeout() {
        responder.set(exchange -> {
            // テストが終わるまで応答しない
            awaitRelease();
            send(exchange, 200, "late");
        });

        assertServiceUnavailable(converter(baseUrl(), SHORT_TIMEOUT));
    }

    @Test
    @DisplayName("AC-296 imgproxy につながらなければ、外部サービスのエラーになる")
    void unavailableWhenUnreachable() throws IOException {
        URI unreachable;
        try (var socket = new ServerSocket(0)) {
            // 使っていないポート（閉じた後は、だれも待ち受けていない）
            unreachable = URI.create("http://127.0.0.1:" + socket.getLocalPort());
        }

        assertServiceUnavailable(converter(unreachable));
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
        return converter(baseUrl, TIMEOUT);
    }

    /**
     * 変換を待つ時間の上限を指定して、テストのサーバーを呼ぶ変換の実装を作る
     *
     * @param baseUrl imgproxy の URL
     * @param timeout 変換を待つ時間の上限
     * @return 変換の実装
     */
    private static ImgproxyImageConverter converter(URI baseUrl, Duration timeout) {
        var properties = new ImgproxyProperties(baseUrl, "0123456789abcdef", "fedcba9876543210", 82, timeout);
        var storage = mock(S3ObjectStorage.class);
        when(storage.uploadSourceUrl("scope/upload")).thenReturn("s3://incoming/scope/upload");
        return new ImgproxyImageConverter(RestClient.builder(), properties, storage);
    }

    /**
     * 変換が、業務のエラー（変換の失敗）になることを確認する
     *
     * @param converter 変換の実装
     */
    private static void assertConversionFailed(ImgproxyImageConverter converter) {
        assertThatThrownBy(() -> converter.convertToWebp("scope/upload"))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorDetail().errorCode().code()).isEqualTo("IMAGE_CONVERSION_FAILED"));
    }

    /**
     * 変換が、外部サービスのエラー（画像のサービスが使えない）になることを確認する
     *
     * @param converter 変換の実装
     */
    private static void assertServiceUnavailable(ImgproxyImageConverter converter) {
        assertThatThrownBy(() -> converter.convertToWebp("scope/upload"))
                .isInstanceOfSatisfying(SystemException.class, ex ->
                        assertThat(ex.getErrorDetail().errorCode().code()).isEqualTo("IMAGE_SERVICE_UNAVAILABLE"));
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

    /**
     * 200 と、形式と本文を返す。
     *
     * @param exchange    要求と応答
     * @param contentType 応答の形式
     * @param body        本文
     * @throws IOException 応答を書けない
     */
    private static void sendBytes(HttpExchange exchange, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, body.length);
        try (var out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}

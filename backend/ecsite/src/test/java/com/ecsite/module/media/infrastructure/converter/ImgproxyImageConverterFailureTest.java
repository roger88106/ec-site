package com.ecsite.module.media.infrastructure.converter;

import com.ecsite.module.media.domain.MediaErrorCode;
import com.ecsite.module.media.infrastructure.storage.MediaStorageProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static com.ecsite.support.BusinessErrorAssertions.assertBusinessError;
import static com.ecsite.support.BusinessErrorAssertions.assertSystemError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * imgproxy の画像の変換の、失敗のときの振る舞いのテスト
 *
 * <p>imgproxy の代わりに、テストの中の小さな HTTP サーバーが応答する。
 * imgproxy が応答した（4xx と 500）ときは業務のエラー（変換の失敗）、imgproxy に届かない・時間の上限を超えたときは
 * 外部サービスのエラーになる。本物の imgproxy での変換は {@code ImgproxyImageConverterTest} が確認する。
 */
class ImgproxyImageConverterFailureTest {

    // ============================== 定数 ==============================

    /**
     * 正常に応答するサーバーを使うテストの、変換を待つ時間の上限
     *
     * <p>負荷のある環境で応答が遅れても、時間の上限を超えたと見なされないよう長くする。
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    /**
     * 時間の上限を確認するテストの、変換を待つ時間の上限
     *
     * <p>応答しないサーバーを待つので短くする。
     */
    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(300);

    /**
     * 署名の鍵（16 進数）
     */
    private static final String KEY = "0123456789abcdef";

    /**
     * 署名のソルト（16 進数）
     */
    private static final String SALT = "fedcba9876543210";

    /**
     * WebP の品質
     */
    private static final int QUALITY = 82;

    /**
     * アップロードのバケット
     */
    private static final String INCOMING_BUCKET = "incoming";

    /**
     * 変換するファイルの、アップロードのバケットのキー
     */
    private static final String UPLOAD_KEY = "scope/upload";

    /**
     * WebP の先頭のバイト
     */
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 4, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};

    /**
     * PNG の先頭のバイト
     */
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 0, 'I', 'H', 'D', 'R'};

    /**
     * imgproxy の代わりの HTTP サーバー
     */
    private HttpServer server;

    /**
     * サーバーの応答の仕方
     *
     * <p>テストごとに決める。
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

    // ============================== テスト ==============================

    @Nested
    @DisplayName("変換できたとき")
    class Converted {

        @Test
        @DisplayName("AC-68 imgproxy が 200 で WebP の本文を返したら、その本文を変換の結果にする")
        void returnsWebpBody() {
            // 準備：WebP を返す
            responder.set(exchange -> sendBytes(exchange, "image/webp", WEBP));

            // 操作と確認：返した本文のまま
            assertThat(converter(baseUrl()).convertToWebp(UPLOAD_KEY)).isEqualTo(WEBP);
        }

        @Test
        @DisplayName("imgproxy には、アップロードのバケットのファイルを指す署名付きの URL でリクエストする")
        void requestsSignedUrlOfUploadedFile() throws Exception {
            // 準備：リクエストのパスを記録して、WebP を返す
            var requested = new AtomicReference<String>();
            responder.set(exchange -> {
                requested.set(exchange.getRequestURI().getRawPath());
                sendBytes(exchange, "image/webp", WEBP);
            });

            // 操作：変換する
            converter(baseUrl()).convertToWebp(UPLOAD_KEY);

            // 確認：設定の鍵とソルトで署名した、変換元を指すパス
            assertThat(requested.get()).isEqualTo(expectedSignedPath());
        }
    }

    @Nested
    @DisplayName("変換の失敗（業務のエラー）")
    class ConversionFailure {

        @Test
        @DisplayName("AC-297 imgproxy が 500 を返したら（壊れたファイルに 500 を返す）、変換の失敗になる")
        void failsOnInternalError() {
            // 準備：500 を返す
            responder.set(exchange -> send(exchange, 500, "Internal error"));

            // 操作と確認：変換の失敗
            assertConversionFailed(converter(baseUrl()));
        }

        @Test
        @DisplayName("AC-297 imgproxy がリクエストを断った（4xx）ら、変換の失敗になる")
        void failsOnClientError() {
            // 準備：422 を返す
            responder.set(exchange -> send(exchange, 422, "unprocessable"));

            // 操作と確認：変換の失敗
            assertConversionFailed(converter(baseUrl()));
        }

        @Test
        @DisplayName("AC-68 imgproxy が 200 で WebP でない本文を返したら、Content-Type が WebP でも変換の失敗になる")
        void failsWhenBodyIsNotWebp() {
            // 準備：Content-Type だけ WebP にして、PNG を返す
            responder.set(exchange -> sendBytes(exchange, "image/webp", PNG));

            // 操作と確認：変換の失敗
            assertConversionFailed(converter(baseUrl()));
        }
    }

    @Nested
    @DisplayName("imgproxy が使えない（外部サービスのエラー）")
    class ServiceUnavailable {

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(ints = {502, 503, 504})
        @DisplayName("AC-296 imgproxy に届かない（手前の代理が中継のエラーを返す）なら、外部サービスのエラーになる")
        void unavailableOnGatewayError(int status) {
            // 準備：手前の代理の中継のエラーを返す
            responder.set(exchange -> send(exchange, status, "unavailable"));

            // 操作と確認：外部サービスのエラー
            assertServiceUnavailable(converter(baseUrl()));
        }

        @Test
        @DisplayName("AC-296 imgproxy の応答が時間の上限を超えたら、外部サービスのエラーになる")
        void unavailableOnTimeout() {
            // 準備：テストが終わるまで応答しない
            responder.set(exchange -> {
                awaitRelease();
                send(exchange, 200, "late");
            });

            // 操作と確認：短い上限で待つと、外部サービスのエラー
            assertServiceUnavailable(converter(baseUrl(), SHORT_TIMEOUT));
        }

        @Test
        @DisplayName("AC-296 imgproxy につながらなければ、外部サービスのエラーになる")
        void unavailableWhenUnreachable() throws IOException {
            // 準備：使っていないポート（閉じた後は、だれも待ち受けていない）
            URI unreachable;
            try (var socket = new ServerSocket(0)) {
                unreachable = URI.create("http://127.0.0.1:" + socket.getLocalPort());
            }

            // 操作と確認：外部サービスのエラー
            assertServiceUnavailable(converter(unreachable));
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 変換が、業務のエラー（変換の失敗）になることを確認する。
     *
     * @param converter 変換の実装
     */
    private static void assertConversionFailed(ImgproxyImageConverter converter) {
        assertBusinessError(() -> converter.convertToWebp(UPLOAD_KEY), MediaErrorCode.IMAGE_CONVERSION_FAILED);
    }

    /**
     * 変換が、外部サービスのエラー（画像のサービスが使えない）になることを確認する。
     *
     * @param converter 変換の実装
     */
    private static void assertServiceUnavailable(ImgproxyImageConverter converter) {
        assertSystemError(() -> converter.convertToWebp(UPLOAD_KEY), MediaErrorCode.IMAGE_SERVICE_UNAVAILABLE);
    }

    /**
     * imgproxy へのリクエストの、期待するパスを定数から作る。
     *
     * <p>変換の指定（品質、WebP）と、変換元（アップロードのバケットのキー）を URL セーフの Base64 にしたものを、
     * 鍵とソルトの HMAC-SHA256 で署名し、署名を先頭に付ける。
     *
     * @return 署名を含むパス
     * @throws GeneralSecurityException 署名を作れない
     */
    private static String expectedSignedPath() throws GeneralSecurityException {
        var source = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("s3://" + INCOMING_BUCKET + "/" + UPLOAD_KEY).getBytes(StandardCharsets.UTF_8));
        var path = "/q:" + QUALITY + "/f:webp/" + source;
        var hex = HexFormat.of();
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(hex.parseHex(KEY), "HmacSHA256"));
        mac.update(hex.parseHex(SALT));
        mac.update(path.getBytes(StandardCharsets.UTF_8));
        var signature = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal());
        return "/" + signature + path;
    }

    // ============================== 準備の補助 ==============================

    /**
     * サーバーの応答の仕方
     */
    @FunctionalInterface
    private interface Responder {
        /**
         * リクエストに応答する。
         *
         * @param exchange リクエストと応答
         * @throws IOException 応答を書けない
         */
        void respond(HttpExchange exchange) throws IOException;
    }

    /**
     * テストのサーバーを呼ぶ変換の実装を作る。
     *
     * @param baseUrl imgproxy の URL
     * @return 変換の実装
     */
    private static ImgproxyImageConverter converter(URI baseUrl) {
        return converter(baseUrl, TIMEOUT);
    }

    /**
     * 変換を待つ時間の上限を指定して、テストのサーバーを呼ぶ変換の実装を作る。
     *
     * @param baseUrl imgproxy の URL
     * @param timeout 変換を待つ時間の上限
     * @return 変換の実装
     */
    private static ImgproxyImageConverter converter(URI baseUrl, Duration timeout) {
        var properties = new ImgproxyProperties(baseUrl, KEY, SALT, QUALITY, timeout);
        var storageProperties = new MediaStorageProperties(null, "us-east-1", true, null, null,
                INCOMING_BUCKET, "public", URI.create("http://cdn.test"), Duration.ofMinutes(5),
                Duration.ofSeconds(10), Duration.ofSeconds(30));
        return new ImgproxyImageConverter(ImgproxyConfig.restClient(RestClient.builder(), properties), properties,
                storageProperties);
    }

    /**
     * テストのサーバーの URL を返す。
     *
     * @return URL
     */
    private URI baseUrl() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    /**
     * テストが終わるまで待つ。
     */
    private void awaitRelease() {
        try {
            release.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 応答を書く。
     *
     * @param exchange リクエストと応答
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
     * @param exchange    リクエストと応答
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

package com.ecsite.module.media.infrastructure.storage;

import com.ecsite.module.media.domain.MediaErrorCode;
import com.sun.net.httpserver.HttpServer;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.ecsite.support.BusinessErrorAssertions.assertSystemError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * S3 のファイルの置き場所の、失敗のときの振る舞いのテスト
 *
 * <p>S3 の代わりに、テストの中の小さな HTTP サーバーが応答する。クライアントはアプリと同じ設定（{@link MediaStorageConfig}）で作る。
 * 接続できない・時間内に応答がない・5xx は外部サービスのエラー、404 は「受付がない」、5xx でないその他は S3 の例外のまま（予期しないエラー）。
 */
class S3ObjectStorageFailureTest {

    // ============================== 定数 ==============================

    /**
     * 正常に応答するサーバーを使うテストの、1 回の試行を待つ時間の上限
     *
     * <p>負荷のある環境で応答が遅れても、時間の上限を超えたと見なされないよう長くする。
     */
    private static final Duration ATTEMPT_TIMEOUT = Duration.ofSeconds(5);

    /**
     * 正常に応答するサーバーを使うテストの、呼び出しの全体を待つ時間の上限
     */
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(15);

    /**
     * 時間の上限を確認するテストの、1 回の試行を待つ時間の上限
     *
     * <p>応答しないサーバーを待つので短くする。
     */
    private static final Duration SHORT_ATTEMPT_TIMEOUT = Duration.ofMillis(300);

    /**
     * 時間の上限を確認するテストの、呼び出しの全体を待つ時間の上限
     *
     * <p>応答しないサーバーを待つので短くする。
     */
    private static final Duration SHORT_CALL_TIMEOUT = Duration.ofSeconds(1);

    /**
     * 時間の上限を確認するテストが、止まったままにならないための上限
     */
    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(10);

    /**
     * アップロードのバケット
     */
    private static final String INCOMING_BUCKET = "incoming";

    /**
     * アップロードのキー
     */
    private static final String UPLOAD_KEY = "scope/upload";

    /**
     * 配信のキー
     */
    private static final String PUBLIC_KEY = "assets/a.webp";

    /**
     * S3 の代わりの HTTP サーバー
     */
    private HttpServer server;

    /**
     * サーバーが返す HTTP の状態
     *
     * <p>テストごとに決める。
     */
    private final AtomicInteger status = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(status.get(), -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("S3 が使えない（外部サービスのエラー）")
    class Unavailable {

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.ecsite.module.media.infrastructure.storage.S3ObjectStorageFailureTest#operations")
        @DisplayName("AC-298 S3 の側の故障（5xx）は、外部サービスのエラー")
        void serverErrorIsUnavailable(String name, Consumer<S3ObjectStorage> operation) {
            // 準備：500 を返す
            status.set(500);

            // 操作と確認：外部サービスのエラー
            assertServiceUnavailable(() -> operation.accept(storage(baseUrl())));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.ecsite.module.media.infrastructure.storage.S3ObjectStorageFailureTest#operations")
        @DisplayName("AC-298 S3 に接続できなければ、外部サービスのエラー")
        void unreachableIsUnavailable(String name, Consumer<S3ObjectStorage> operation) throws IOException {
            // 準備：使っていないポート（閉じた後は、だれも待ち受けていない）
            URI unreachable;
            try (var socket = new ServerSocket(0)) {
                unreachable = URI.create("http://127.0.0.1:" + socket.getLocalPort());
            }

            // 操作と確認：外部サービスのエラー
            assertServiceUnavailable(() -> operation.accept(storage(unreachable)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.ecsite.module.media.infrastructure.storage.S3ObjectStorageFailureTest#operations")
        @DisplayName("AC-301、303 応答しない S3 は、待つ時間の上限で外部サービスのエラーになる")
        void silentServerTimesOut(String name, Consumer<S3ObjectStorage> operation) throws IOException {
            // 準備：接続は受け付けるが、何も返さない（受け付けの待ち行列に入るだけ）
            try (var silent = new ServerSocket(0)) {
                var storage = storage(URI.create("http://127.0.0.1:" + silent.getLocalPort()),
                        SHORT_ATTEMPT_TIMEOUT, SHORT_CALL_TIMEOUT);

                // 操作と確認：短い上限で、外部サービスのエラー（テストが止まったままにならないよう、テストにも上限を置く）
                assertTimeoutPreemptively(TEST_TIMEOUT,
                        () -> assertServiceUnavailable(() -> operation.accept(storage)));
            }
        }
    }

    @Nested
    @DisplayName("5xx でない応答")
    class OtherResponses {

        @Test
        @DisplayName("アップロードしたファイルがなければ（404）、大きさが空になる")
        void notFoundIsEmpty() {
            // 準備：404 を返す
            status.set(404);

            // 操作と確認：大きさが空
            assertThat(storage(baseUrl()).findUploadSize(UPLOAD_KEY)).isEmpty();
        }

        @Test
        @DisplayName("5xx でないその他の失敗（権限がないなど）は、S3 の例外のまま（予期しないエラー）")
        void forbiddenIsNotUnavailable() {
            // 準備：403 を返す
            status.set(403);

            // 操作と確認：403 の S3 の例外のまま
            assertThatThrownBy(() -> storage(baseUrl()).putPublished(PUBLIC_KEY, new byte[]{1}, "image/webp"))
                    .isInstanceOfSatisfying(S3Exception.class, e -> assertThat(e.statusCode()).isEqualTo(403));
        }
    }

    @Nested
    @DisplayName("アップロードの受付の署名付き URL")
    class PresignUpload {

        @Test
        @DisplayName("AC-298 署名付き URL は S3 を呼ばずに作るので、S3 に接続できなくても発行できる")
        void presignsWithoutCallingS3() throws IOException {
            // 準備：使っていないポート（閉じた後は、だれも待ち受けていない）
            URI unreachable;
            try (var socket = new ServerSocket(0)) {
                unreachable = URI.create("http://127.0.0.1:" + socket.getLocalPort());
            }
            var properties = properties(unreachable, ATTEMPT_TIMEOUT, CALL_TIMEOUT);
            var config = new MediaStorageConfig();
            try (var client = config.mediaS3Client(properties); var presigner = config.mediaS3Presigner(properties)) {
                var storage = new S3ObjectStorage(client, presigner, properties);

                // 操作：署名付き URL を発行する
                var upload = storage.presignUpload(UPLOAD_KEY, "image/png", 100);

                // 確認：アップロードのバケットのキーを指す URL ができる
                assertThat(upload.url().toString()).startsWith(unreachable + "/" + INCOMING_BUCKET + "/" + UPLOAD_KEY + "?");
            }
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 外部サービスのエラー（画像のサービスが使えない）になることを確認する。
     *
     * @param call 確認する呼び出し
     */
    private static void assertServiceUnavailable(ThrowingCallable call) {
        assertSystemError(call, MediaErrorCode.IMAGE_SERVICE_UNAVAILABLE);
    }

    // ============================== 準備の補助 ==============================

    /**
     * 失敗の分類を確認する操作を返す。
     *
     * <p>署名付き URL の発行は S3 を呼ばないので除く。
     *
     * @return 操作の名前と、呼び出し
     */
    static Stream<Arguments> operations() {
        return Stream.of(
                Arguments.of("受付の大きさを読む", (Consumer<S3ObjectStorage>) s -> s.findUploadSize(UPLOAD_KEY)),
                Arguments.of("受付の先頭を読む", (Consumer<S3ObjectStorage>) s -> s.readUploadHead(UPLOAD_KEY, 16)),
                Arguments.of("配信のバケットに書く",
                        (Consumer<S3ObjectStorage>) s -> s.putPublished(PUBLIC_KEY, new byte[]{1}, "image/webp")),
                Arguments.of("配信のファイルを消す", (Consumer<S3ObjectStorage>) s -> s.deletePublished(PUBLIC_KEY)));
    }

    /**
     * 指定の S3 を呼ぶ実装を、アプリと同じ設定のクライアントで作る（署名付き URL は使わない）。
     *
     * @param endpoint S3 の URL
     * @return S3 を呼ぶ実装
     */
    private static S3ObjectStorage storage(URI endpoint) {
        return storage(endpoint, ATTEMPT_TIMEOUT, CALL_TIMEOUT);
    }

    /**
     * 待つ時間の上限を指定して、指定の S3 を呼ぶ実装を作る（署名付き URL は使わない）。
     *
     * @param endpoint       S3 の URL
     * @param attemptTimeout 1 回の試行を待つ時間の上限
     * @param callTimeout    呼び出し全体を待つ時間の上限
     * @return S3 を呼ぶ実装
     */
    private static S3ObjectStorage storage(URI endpoint, Duration attemptTimeout, Duration callTimeout) {
        var properties = properties(endpoint, attemptTimeout, callTimeout);
        S3Client client = new MediaStorageConfig().mediaS3Client(properties);
        return new S3ObjectStorage(client, null, properties);
    }

    /**
     * 指定の S3 と待つ時間の上限で、ファイルの置き場所の設定を作る。
     *
     * @param endpoint       S3 の URL
     * @param attemptTimeout 1 回の試行を待つ時間の上限
     * @param callTimeout    呼び出し全体を待つ時間の上限
     * @return 設定
     */
    private static MediaStorageProperties properties(URI endpoint, Duration attemptTimeout, Duration callTimeout) {
        return new MediaStorageProperties(endpoint, "us-east-1", true, "test-key", "test-secret",
                INCOMING_BUCKET, "public", URI.create("http://cdn.test"), Duration.ofMinutes(5),
                attemptTimeout, callTimeout);
    }

    /**
     * テストのサーバーの URL を返す。
     *
     * @return URL
     */
    private URI baseUrl() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }
}

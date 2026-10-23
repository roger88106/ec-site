package com.ecsite.module.media.infrastructure.storage;

import com.ecsite.common.error.exception.SystemException;
import com.sun.net.httpserver.HttpServer;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * ファイルの置き場所の S3 の実装の、失敗のときの振る舞いのテスト（T017-03-05、docs/design/modules/media.md）。<br />
 * S3 の代わりに、テストの中の小さな HTTP サーバーが応答する。クライアントはアプリと同じ設定（{@link MediaStorageConfig}）で作る。
 * 接続できない・時間内に応答がない・5xx は外部サービスのエラー、404 は「受付がない」、5xx でないその他は S3 の例外のまま（予期しないエラー）。
 */
class S3ObjectStorageFailureTest {

    /**
     * 正常に応答するサーバーを使うテストの、1 回の試行を待つ時間の上限。
     * 負荷のある環境で応答が遅れても、時間の上限を超えたと見なされないよう長くする。
     */
    private static final Duration ATTEMPT_TIMEOUT = Duration.ofSeconds(5);

    /**
     * 正常に応答するサーバーを使うテストの、呼び出しの全体を待つ時間の上限
     */
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(15);

    /**
     * 時間の上限を確かめるテストの、1 回の試行を待つ時間の上限（応答しないサーバーを待つので短くする）
     */
    private static final Duration SHORT_ATTEMPT_TIMEOUT = Duration.ofMillis(300);

    /**
     * 時間の上限を確かめるテストの、呼び出しの全体を待つ時間の上限（短くする）
     */
    private static final Duration SHORT_CALL_TIMEOUT = Duration.ofSeconds(1);

    /**
     * S3 の代わりの HTTP サーバー
     */
    private HttpServer server;

    /**
     * サーバーが返す HTTP の状態（テストごとに決める）
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("operations")
    @DisplayName("AC-298 S3 の側の故障（5xx）は、外部サービスのエラー")
    void serverErrorIsUnavailable(String name, Consumer<S3ObjectStorage> operation) {
        status.set(500);

        assertServiceUnavailable(() -> operation.accept(storage(baseUrl())));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("operations")
    @DisplayName("AC-298 S3 に接続できなければ、外部サービスのエラー")
    void unreachableIsUnavailable(String name, Consumer<S3ObjectStorage> operation) throws IOException {
        URI unreachable;
        try (var socket = new ServerSocket(0)) {
            // 使っていないポート（閉じた後は、だれも待ち受けていない）
            unreachable = URI.create("http://127.0.0.1:" + socket.getLocalPort());
        }

        assertServiceUnavailable(() -> operation.accept(storage(unreachable)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("operations")
    @DisplayName("AC-301、303 応答しない S3 は、待つ時間の上限で外部サービスのエラーになる")
    void silentServerTimesOut(String name, Consumer<S3ObjectStorage> operation) throws IOException {
        // 接続は受け付けるが、何も返さない（受け付けの待ち行列に入るだけ）
        try (var silent = new ServerSocket(0)) {
            var storage = storage(URI.create("http://127.0.0.1:" + silent.getLocalPort()),
                    SHORT_ATTEMPT_TIMEOUT, SHORT_CALL_TIMEOUT);

            assertTimeoutPreemptively(Duration.ofSeconds(10),
                    () -> assertServiceUnavailable(() -> operation.accept(storage)));
        }
    }

    @Test
    @DisplayName("アップロードしたファイルがなければ（404）、今どおり大きさが空になる")
    void notFoundIsEmpty() {
        status.set(404);

        assertThat(storage(baseUrl()).findUploadSize("scope/upload")).isEmpty();
    }

    @Test
    @DisplayName("5xx でないその他の失敗（権限がないなど）は、S3 の例外のまま（予期しないエラー）")
    void forbiddenIsNotUnavailable() {
        status.set(403);

        assertThatThrownBy(() -> storage(baseUrl()).putPublished("assets/a.webp", new byte[]{1}, "image/webp"))
                .isInstanceOfSatisfying(S3Exception.class, e -> assertThat(e.statusCode()).isEqualTo(403));
    }

    /**
     * 失敗の分類を確認する操作（名前、呼び出し）。署名付き URL の発行は S3 を呼ばないので除く
     */
    static Stream<Arguments> operations() {
        return Stream.of(
                Arguments.of("受付の大きさを読む", (Consumer<S3ObjectStorage>) s -> s.findUploadSize("scope/upload")),
                Arguments.of("受付の先頭を読む", (Consumer<S3ObjectStorage>) s -> s.readUploadHead("scope/upload", 16)),
                Arguments.of("配信のバケットに書く",
                        (Consumer<S3ObjectStorage>) s -> s.putPublished("assets/a.webp", new byte[]{1}, "image/webp")),
                Arguments.of("配信のファイルを消す", (Consumer<S3ObjectStorage>) s -> s.deletePublished("assets/a.webp")));
    }

    // ============================== 補助 ==============================

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
     * 待つ時間の上限を指定して、指定の S3 を呼ぶ実装を作る。
     *
     * @param endpoint       S3 の URL
     * @param attemptTimeout 1 回の試行を待つ時間の上限
     * @param callTimeout    呼び出し全体を待つ時間の上限
     * @return S3 を呼ぶ実装
     */
    private static S3ObjectStorage storage(URI endpoint, Duration attemptTimeout, Duration callTimeout) {
        var properties = new MediaStorageProperties(null, "us-east-1", true, "test-key", "test-secret",
                "incoming", "public", URI.create("http://cdn.test"), Duration.ofMinutes(5),
                Duration.ofSeconds(10), Duration.ofSeconds(30))
                .withEndpoint(endpoint, attemptTimeout, callTimeout);
        S3Client client = MediaStorageConfig.s3Client(properties);
        return new S3ObjectStorage(client, null, properties);
    }

    /**
     * 外部サービスのエラー（画像のサービスが使えない）になることを確認する。
     */
    private static void assertServiceUnavailable(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(SystemException.class, ex ->
                assertThat(ex.getErrorDetail().errorCode().code()).isEqualTo("IMAGE_SERVICE_UNAVAILABLE"));
    }

    /**
     * テストのサーバーの URL
     */
    private URI baseUrl() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }
}

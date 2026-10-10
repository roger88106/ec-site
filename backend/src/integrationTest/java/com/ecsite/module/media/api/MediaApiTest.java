package com.ecsite.module.media.api;

import com.ecsite.IntegrationTest;
import com.ecsite.TestcontainersConfig;
import com.ecsite.common.error.exception.BusinessException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.ecsite.jooq.media.Tables.ASSET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * メディアモジュールの API の統合テスト（T1028。docs/modules/media.md）。<br />
 * ファイルは、受付の署名付き URL へ実際に PUT する（Testcontainers の SeaweedFS）。
 * 解放したファイルの削除はコミット後のイベントで行うので、このテストはロールバックせず、テストの後に作ったアセットを消す。
 * エラーコードは、商品画像 API の契約と同じ文字列であることを確かめる。
 */
@IntegrationTest
class MediaApiTest {

    /**
     * 画像の大きさの上限（10MB。AC-39）
     */
    private static final int MAX_BYTES = 10 * 1024 * 1024;

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R'};

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};

    private static final byte[] GIF = {'G', 'I', 'F', '8', '9', 'a', 1, 0, 1, 0, 0, 0};

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    MediaApi media;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    DSLContext dsl;

    @Autowired
    S3Client s3;

    /**
     * このテストで確定したアセットの ID（後で記録を消す）
     */
    private final List<UUID> assetIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        dsl.deleteFrom(ASSET).where(ASSET.ID.in(assetIds)).execute();
    }

    @Nested
    @DisplayName("受付")
    class RequestUpload {

        @Test
        @DisplayName("AC-39 JPEG、PNG、WebP は受け付け、署名付き URL を返す")
        void acceptsSupportedFormats() throws Exception {
            for (var contentType : List.of("image/jpeg", "image/png", "image/webp")) {
                var upload = media.requestImageUpload(scope(), contentType, 100);

                assertThat(upload.uploadId()).isNotNull();
                assertThat(upload.headers()).containsEntry("Content-Type", contentType);
                assertThat(upload.expiresAt()).isAfter(java.time.Instant.now());
            }
            var upload = media.requestImageUpload(scope(), "image/png", PNG.length);
            assertThat(put(upload, PNG)).isEqualTo(200);
        }

        @Test
        @DisplayName("AC-39 対象外の形式は拒否する")
        void rejectsUnsupportedFormat() {
            assertErrorCode(() -> media.requestImageUpload(scope(), "image/gif", 100), "UNSUPPORTED_IMAGE_FORMAT");
        }

        @Test
        @DisplayName("AC-39 0 バイトと、10MB を超える大きさは拒否する")
        void rejectsInvalidSize() {
            assertErrorCode(() -> media.requestImageUpload(scope(), "image/png", 0), "IMAGE_TOO_LARGE");
            assertErrorCode(() -> media.requestImageUpload(scope(), "image/png", MAX_BYTES + 1), "IMAGE_TOO_LARGE");
            assertThatCode(() -> media.requestImageUpload(scope(), "image/png", MAX_BYTES)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("範囲は英数字とハイフンだけ（キーの区切りを含められない）")
        void rejectsInvalidScope() {
            assertThatThrownBy(() -> media.requestImageUpload("../other", "image/png", 100))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> media.requestImageUpload("", "image/png", 100))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("確定")
    class Confirm {

        @Test
        @DisplayName("AC-37 確定すると、アセットが記録され、配信の URL でファイルを取得できる")
        void confirms() throws Exception {
            var scope = scope();
            var upload = uploaded(scope, "image/png", PNG);

            var assetId = confirm(scope, upload.uploadId());

            assertThat(dsl.fetchExists(ASSET, ASSET.ID.eq(assetId))).isTrue();
            var url = media.urls(List.of(assetId)).get(assetId);
            assertThat(url.toString()).endsWith("/assets/" + assetId + ".png");
            var response = get(url);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).containsExactly(PNG);
            assertThat(response.headers().firstValue("Content-Type")).contains("image/png");
        }

        @Test
        @DisplayName("AC-39 形式は申告ではなく先頭のバイトで判定する（PNG と申告した JPEG は JPEG で配信する）")
        void detectsFormatFromContent() throws Exception {
            var scope = scope();
            var upload = uploaded(scope, "image/png", JPEG);

            var assetId = confirm(scope, upload.uploadId());

            var url = media.urls(List.of(assetId)).get(assetId);
            assertThat(url.toString()).endsWith("/assets/" + assetId + ".jpg");
            assertThat(get(url).headers().firstValue("Content-Type")).contains("image/jpeg");
        }

        @Test
        @DisplayName("AC-39 中身が対象の画像でないファイルは拒否する")
        void rejectsInvalidFile() throws Exception {
            var scope = scope();
            var upload = uploaded(scope, "image/png", GIF);

            assertErrorCode(() -> media.confirmImage(scope, upload.uploadId()), "INVALID_IMAGE_FILE");
        }

        @Test
        @DisplayName("AC-39 受付のファイルが 10MB を超えていれば拒否する（署名付き URL を通さずに置かれたもの）")
        void rejectsOversizedFile() {
            var scope = scope();
            var uploadId = UUID.randomUUID();
            var body = new byte[MAX_BYTES + 1];
            System.arraycopy(PNG, 0, body, 0, PNG.length);
            s3.putObject(request -> request.bucket(TestcontainersConfig.INCOMING_BUCKET).key(scope + "/" + uploadId),
                    RequestBody.fromBytes(body));

            assertErrorCode(() -> media.confirmImage(scope, uploadId), "IMAGE_TOO_LARGE");
        }

        @Test
        @DisplayName("受付のない uploadId、別の範囲の受付は拒否する")
        void rejectsMissingUpload() throws Exception {
            var upload = uploaded(scope(), "image/png", PNG);

            assertErrorCode(() -> media.confirmImage(scope(), UUID.randomUUID()), "IMAGE_UPLOAD_NOT_FOUND");
            assertErrorCode(() -> media.confirmImage(scope(), upload.uploadId()), "IMAGE_UPLOAD_NOT_FOUND");
        }

        @Test
        @DisplayName("呼んだ側のトランザクションが取り消されると、アセットの記録も残らない")
        void rollsBackWithCaller() throws Exception {
            var scope = scope();
            var upload = uploaded(scope, "image/png", PNG);

            var assetId = transactionTemplate.execute(status -> {
                var id = media.confirmImage(scope, upload.uploadId());
                status.setRollbackOnly();
                return id;
            });

            assertThat(dsl.fetchExists(ASSET, ASSET.ID.eq(assetId))).isFalse();
            assertThat(media.urls(List.of(assetId))).isEmpty();
        }
    }

    @Nested
    @DisplayName("解放")
    class Release {

        @Test
        @DisplayName("手放すと記録が消え、コミットの後に配信のファイルが消える")
        void releases() throws Exception {
            var assetId = confirmed();
            var url = media.urls(List.of(assetId)).get(assetId);

            transactionTemplate.executeWithoutResult(status -> media.release(List.of(assetId)));

            assertThat(dsl.fetchExists(ASSET, ASSET.ID.eq(assetId))).isFalse();
            assertThat(media.urls(List.of(assetId))).isEmpty();
            await().atMost(TIMEOUT).untilAsserted(() -> assertThat(get(url).statusCode()).isEqualTo(404));
        }

        @Test
        @DisplayName("呼んだ側のトランザクションが取り消されると、記録もファイルも残る")
        void keepsAssetOnRollback() throws Exception {
            var assetId = confirmed();
            var url = media.urls(List.of(assetId)).get(assetId);

            transactionTemplate.executeWithoutResult(status -> {
                media.release(List.of(assetId));
                status.setRollbackOnly();
            });

            assertThat(dsl.fetchExists(ASSET, ASSET.ID.eq(assetId))).isTrue();
            // 消すイベントが届いていないことを、少し待ってから確かめる
            Thread.sleep(1_000);
            assertThat(get(url).statusCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("すでにないアセットを手放しても成功する")
        void ignoresReleasedAsset() throws Exception {
            var assetId = confirmed();
            transactionTemplate.executeWithoutResult(status -> media.release(List.of(assetId)));

            assertThatCode(() -> transactionTemplate.executeWithoutResult(status ->
                    media.release(List.of(assetId, UUID.randomUUID())))).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("配信の URL")
    class Urls {

        @Test
        @DisplayName("複数のアセットの URL をまとめて返し、ないアセットは含めない")
        void returnsUrlsInBatch() throws Exception {
            var first = confirmed();
            var second = confirmed();
            var unknown = UUID.randomUUID();

            var urls = media.urls(List.of(first, second, unknown));

            assertThat(urls.keySet()).isEqualTo(Set.of(first, second));
            assertThat(media.urls(List.of())).isEmpty();
        }
    }

    // ============================== 補助 ==============================

    private static String scope() {
        return UUID.randomUUID().toString();
    }

    /**
     * 受付をして、ファイルを署名付き URL へ PUT する
     */
    private ImageUpload uploaded(String scope, String contentType, byte[] body) throws Exception {
        var upload = media.requestImageUpload(scope, contentType, body.length);
        assertThat(put(upload, body)).isEqualTo(200);
        return upload;
    }

    private UUID confirm(String scope, UUID uploadId) {
        var assetId = media.confirmImage(scope, uploadId);
        assetIds.add(assetId);
        return assetId;
    }

    /**
     * PNG を受け付けて確定し、アセット ID を返す
     */
    private UUID confirmed() throws Exception {
        var scope = scope();
        return confirm(scope, uploaded(scope, "image/png", PNG).uploadId());
    }

    private static void assertErrorCode(ThrowingCallable callable, String errorCode) {
        assertThatThrownBy(callable).isInstanceOfSatisfying(BusinessException.class, ex ->
                assertThat(ex.getErrorDetail().errorCode().code()).isEqualTo(errorCode));
    }

    private int put(ImageUpload upload, byte[] body) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(upload.uploadUrl()).PUT(HttpRequest.BodyPublishers.ofByteArray(body));
        upload.headers().forEach(request::header);
        return http.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private HttpResponse<byte[]> get(URI url) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(url).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }
}

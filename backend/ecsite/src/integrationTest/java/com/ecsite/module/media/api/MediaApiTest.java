package com.ecsite.module.media.api;

import com.ecsite.IntegrationTest;
import com.ecsite.TestImages;
import com.ecsite.TestcontainersConfig;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.media.application.port.ObjectStorage;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.ecsite.AsyncEvents.TIMEOUT;
import static com.ecsite.jooq.common.Tables.EVENT_PUBLICATION;
import static com.ecsite.jooq.media.Tables.ASSET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

/**
 * メディアモジュールの API の統合テスト（T1028、T1037、T017-04-01、T017-04-10。docs/backend/modules/media.md）。<br />
 * 用意では imgproxy で WebP に変換するので、本物の画像（{@link TestImages}）を使う。
 * ファイルは、受付の署名付き URL へ実際に PUT する（Testcontainers の SeaweedFS）。
 * 解放したファイルの削除はコミット後のイベントで行うので、このテストはロールバックせず、テストの後に作ったアセットを消す。
 * エラーコードは、商品画像 API の契約と同じ文字列であることを確かめる。
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class MediaApiTest {

    /**
     * 画像の大きさの上限（10MB。AC-39）
     */
    private static final int MAX_BYTES = 10 * 1024 * 1024;

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
     * ファイルの置き場所（配信のバケットへの書き込み・削除の失敗を起こすため、本物を包む）
     */
    @MockitoSpyBean
    ObjectStorage storage;

    /**
     * このテストで登録したアセットの ID（後で記録を消す）
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
        @DisplayName("AC-39、67 JPEG、PNG、WebP、BMP、HEIC / HEIF、AVIF は受け付け、署名付き URL を返す")
        void acceptsSupportedFormats() throws Exception {
            for (var contentType : List.of(
                    "image/jpeg", "image/png", "image/webp", "image/bmp", "image/heic", "image/heif", "image/avif")) {
                var upload = media.requestImageUpload(scope(), contentType, 100);

                assertThat(upload.uploadId()).isNotNull();
                assertThat(upload.headers()).containsEntry("Content-Type", contentType);
                assertThat(upload.expiresAt()).isAfter(java.time.Instant.now());
            }
            var png = TestImages.png();
            var upload = media.requestImageUpload(scope(), "image/png", png.length);
            assertThat(put(upload, png)).isEqualTo(200);
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
    @DisplayName("用意")
    class Prepare {

        @Test
        @DisplayName("AC-304 用意は WebP に変換して配信のバケットに置き、アセットを記録しない")
        void preparesWithoutRecord() throws Exception {
            var scope = scope();
            var upload = uploaded(scope, "image/png", TestImages.png());

            var prepared = media.prepareImage(scope, upload.uploadId());

            assertThat(dsl.fetchExists(ASSET, ASSET.ID.eq(prepared.assetId()))).isFalse();
            var response = getPublished(prepared);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(new String(response.body(), 8, 4, StandardCharsets.US_ASCII)).isEqualTo("WEBP");
            assertThat(response.headers().firstValue("Content-Type")).contains("image/webp");
            media.discardImage(prepared);
        }

        @ParameterizedTest(name = "{0}（{1}）")
        @CsvSource({
                "source.jpg, image/jpeg",
                "source.webp, image/webp",
                "source.bmp, image/bmp",
                "source.heic, image/heic",
                "source.heic, image/heif",
                "source.avif, image/avif",
        })
        @DisplayName("AC-67、68 スマートフォンの形式を含め、どの形式も WebP にして配信する")
        void convertsEveryFormat(String file, String contentType) throws Exception {
            var scope = scope();
            var upload = uploaded(scope, contentType, TestImages.of(file));

            var assetId = confirm(scope, upload.uploadId());

            var url = media.urls(List.of(assetId)).get(assetId);
            assertThat(url.toString()).endsWith(".webp");
            assertThat(get(url).headers().firstValue("Content-Type")).contains("image/webp");
        }

        @Test
        @DisplayName("AC-39 形式は申告ではなく先頭のバイトで判定する（PNG と申告した JPEG も変換できる）")
        void detectsFormatFromContent() throws Exception {
            var scope = scope();
            var upload = uploaded(scope, "image/png", TestImages.jpeg());

            var assetId = confirm(scope, upload.uploadId());

            assertThat(media.urls(List.of(assetId)).get(assetId).toString()).endsWith(".webp");
        }

        @Test
        @DisplayName("AC-70 変換できない（壊れた）ファイルは拒否し、アセットを残さない")
        void rejectsUnconvertibleFile() throws Exception {
            var scope = scope();
            var upload = uploaded(scope, "image/heic", TestImages.of("broken.heic"));
            var before = assetIds();

            assertErrorCode(() -> media.prepareImage(scope, upload.uploadId()), "IMAGE_CONVERSION_FAILED");
            assertThat(assetIds()).isSubsetOf(before);
        }

        @Test
        @DisplayName("配信のバケットに書けなければ、用意は失敗する")
        void failsWhenPublishingFails() throws Exception {
            var scope = scope();
            var upload = uploaded(scope, "image/png", TestImages.png());
            doThrow(S3Exception.builder().statusCode(404).message("The specified bucket does not exist").build())
                    .when(storage).putPublished(anyString(), any(), anyString());

            assertThatThrownBy(() -> media.prepareImage(scope, upload.uploadId())).isInstanceOf(S3Exception.class);
        }

        @Test
        @DisplayName("AC-39 中身が対象の画像でないファイルは拒否する")
        void rejectsInvalidFile() throws Exception {
            var scope = scope();
            var upload = uploaded(scope, "image/png", GIF);

            assertErrorCode(() -> media.prepareImage(scope, upload.uploadId()), "INVALID_IMAGE_FILE");
        }

        @Test
        @DisplayName("AC-39 アップロードしたファイルが 10MB を超えていれば拒否する（署名付き URL を通さずに置かれたもの）")
        void rejectsOversizedFile() {
            var scope = scope();
            var uploadId = UUID.randomUUID();
            var body = new byte[MAX_BYTES + 1];
            var png = TestImages.png();
            System.arraycopy(png, 0, body, 0, png.length);
            s3.putObject(request -> request.bucket(TestcontainersConfig.INCOMING_BUCKET).key(scope + "/" + uploadId),
                    RequestBody.fromBytes(body));

            assertErrorCode(() -> media.prepareImage(scope, uploadId), "IMAGE_TOO_LARGE");
        }

        @Test
        @DisplayName("アップロードのない uploadId、別の範囲のアップロードは拒否する")
        void rejectsMissingUpload() throws Exception {
            var upload = uploaded(scope(), "image/png", TestImages.png());

            assertErrorCode(() -> media.prepareImage(scope(), UUID.randomUUID()), "IMAGE_UPLOAD_NOT_FOUND");
            assertErrorCode(() -> media.prepareImage(scope(), upload.uploadId()), "IMAGE_UPLOAD_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("登録")
    class Register {

        @Test
        @DisplayName("AC-37、68 用意した画像を登録すると、アセットが記録され、配信の URL で取得できる")
        void registers() throws Exception {
            var scope = scope();
            var upload = uploaded(scope, "image/png", TestImages.png());

            var assetId = confirm(scope, upload.uploadId());

            assertThat(dsl.fetchExists(ASSET, ASSET.ID.eq(assetId))).isTrue();
            assertThat(dsl.fetchValue(ASSET.FORMAT, ASSET.ID.eq(assetId))).isEqualTo("WEBP");
            var url = media.urls(List.of(assetId)).get(assetId);
            assertThat(url.toString()).endsWith("/assets/" + assetId + ".webp");
            assertThat(get(url).statusCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("AC-304 登録は呼んだ側のトランザクションの中で行い、トランザクションがなければ失敗する")
        void requiresTransaction() throws Exception {
            var scope = scope();
            var prepared = media.prepareImage(scope, uploaded(scope, "image/png", TestImages.png()).uploadId());

            assertThatThrownBy(() -> media.registerImage(prepared)).isInstanceOf(IllegalTransactionStateException.class);
            assertThat(dsl.fetchExists(ASSET, ASSET.ID.eq(prepared.assetId()))).isFalse();
            media.discardImage(prepared);
        }

        @Test
        @DisplayName("呼んだ側のトランザクションが取り消されると、アセットの記録も残らない")
        void rollsBackWithCaller() throws Exception {
            var scope = scope();
            var prepared = media.prepareImage(scope, uploaded(scope, "image/png", TestImages.png()).uploadId());

            transactionTemplate.executeWithoutResult(status -> {
                media.registerImage(prepared);
                status.setRollbackOnly();
            });

            assertThat(dsl.fetchExists(ASSET, ASSET.ID.eq(prepared.assetId()))).isFalse();
            assertThat(media.urls(List.of(prepared.assetId()))).isEmpty();
            media.discardImage(prepared);
        }
    }

    @Nested
    @DisplayName("破棄")
    class Discard {

        @Test
        @DisplayName("AC-305 破棄すると、用意で置いた配信のファイルが消える")
        void discards() throws Exception {
            var scope = scope();
            var prepared = media.prepareImage(scope, uploaded(scope, "image/png", TestImages.png()).uploadId());

            media.discardImage(prepared);

            assertThat(getPublished(prepared).statusCode()).isEqualTo(404);
        }

        @Test
        @DisplayName("AC-305 消すのに失敗しても例外を投げず、警告のログに保存先のキーを出す")
        void logsWhenDeleteFails(CapturedOutput output) throws Exception {
            var scope = scope();
            var prepared = media.prepareImage(scope, uploaded(scope, "image/png", TestImages.png()).uploadId());
            doThrow(SdkClientException.create("connection refused")).when(storage).deletePublished(anyString());

            assertThatCode(() -> media.discardImage(prepared)).doesNotThrowAnyException();

            assertThat(output).containsPattern("WARN.*assets/" + prepared.assetId() + "\\.webp");
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
            assertThat(releaseRegistered(assetId)).isTrue();
            await().atMost(TIMEOUT).untilAsserted(() -> assertThat(get(url).statusCode()).isEqualTo(404));
        }

        @Test
        @DisplayName("AC-306 ファイルを消すリスナーは、データベースのトランザクションの外でファイル保存を呼ぶ")
        void deletesOutsideTransaction() throws Exception {
            var assetId = confirmed();
            var transactionActive = new CopyOnWriteArrayList<Boolean>();
            doAnswer(invocation -> {
                transactionActive.add(TransactionSynchronizationManager.isActualTransactionActive());
                return invocation.callRealMethod();
            }).when(storage).deletePublished(anyString());

            transactionTemplate.executeWithoutResult(status -> media.release(List.of(assetId)));

            await().atMost(TIMEOUT).until(() -> !transactionActive.isEmpty());
            assertThat(transactionActive).containsExactly(false);
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
            // 取り消されたトランザクションでは、ファイルを消すイベントが登録されない（待たずに確かめられる）
            assertThat(releaseRegistered(assetId)).isFalse();
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

    /**
     * アセット解放のイベントが、イベント発行記録に登録されたか
     *
     * @param assetId アセット ID
     * @return 登録されていれば true
     */
    private boolean releaseRegistered(UUID assetId) {
        return dsl.fetchExists(EVENT_PUBLICATION, EVENT_PUBLICATION.EVENT_TYPE.endsWith(".AssetReleased")
                .and(EVENT_PUBLICATION.SERIALIZED_EVENT.contains(assetId.toString())));
    }

    /**
     * 今あるアセットの ID。アセットは受付の範囲を持たないので、ID の集まりで、新しく作られていないことを確かめる
     * （ほかのテストが消すことはあるので、数では比べない）
     */
    private Set<UUID> assetIds() {
        return dsl.select(ASSET.ID).from(ASSET).fetchSet(ASSET.ID);
    }

    /**
     * テストごとに別の範囲（アップロードの持ち主）を作る
     */
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

    /**
     * 受け付けた画像を用意し、トランザクションの中で登録する。テストの後に消すためにアセット ID を覚えておく
     */
    private UUID confirm(String scope, UUID uploadId) {
        var prepared = media.prepareImage(scope, uploadId);
        var assetId = transactionTemplate.execute(status -> media.registerImage(prepared));
        assetIds.add(assetId);
        return assetId;
    }

    /**
     * PNG を受け付けて用意・登録し、アセット ID を返す
     */
    private UUID confirmed() throws Exception {
        var scope = scope();
        return confirm(scope, uploaded(scope, "image/png", TestImages.png()).uploadId());
    }

    /**
     * 処理が、指定したエラーコード（文字列）の業務のエラーで失敗することを確かめる
     */
    private static void assertErrorCode(ThrowingCallable callable, String errorCode) {
        assertThatThrownBy(callable).isInstanceOfSatisfying(BusinessException.class, ex ->
                assertThat(ex.getErrorDetail().errorCode().code()).isEqualTo(errorCode));
    }

    /**
     * 署名付き URL へ、指定のヘッダーを付けてファイルを PUT し、HTTP ステータスを返す
     */
    private int put(ImageUpload upload, byte[] body) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(upload.uploadUrl()).PUT(HttpRequest.BodyPublishers.ofByteArray(body));
        upload.headers().forEach(request::header);
        return http.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    /**
     * 配信の URL を、認証なしで取得する
     */
    private HttpResponse<byte[]> get(URI url) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(url).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    /**
     * 用意した画像の配信のファイルを、記録を通さずに取得する（登録の前は配信の URL を引けない）
     */
    private HttpResponse<byte[]> getPublished(PreparedImage prepared) throws IOException, InterruptedException {
        return get(storage.publicUrl("assets/" + prepared.assetId() + ".webp"));
    }
}

package com.ecsite.module.media.api;

import com.ecsite.common.model.id.AssetId;
import com.ecsite.common.model.id.UploadId;
import com.ecsite.module.media.api.model.ImageUpload;
import com.ecsite.module.media.api.model.PreparedImage;
import com.ecsite.module.media.application.MediaService;
import com.ecsite.module.media.application.port.ObjectStorage;
import com.ecsite.module.media.application.port.PendingAssetRepository;
import com.ecsite.module.media.domain.MediaErrorCode;
import com.ecsite.support.LogCapture;
import com.ecsite.support.MutableClock;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.config.TestcontainersConfig;
import com.ecsite.support.data.TestImages;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
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
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static com.ecsite.jooq.common.Tables.EVENT_PUBLICATION;
import static com.ecsite.jooq.media.Tables.ASSET;
import static com.ecsite.jooq.media.Tables.PENDING_ASSET;
import static com.ecsite.support.BusinessErrorAssertions.assertBusinessError;
import static com.ecsite.support.async.AsyncEvents.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * メディアの API の統合テスト
 *
 * <p>用意では imgproxy で WebP に変換するので、本物の画像（{@link TestImages}）を使う。
 * ファイルは、受付の署名付き URL へ実際に PUT する（Testcontainers の SeaweedFS）。
 * 解放したファイルの削除はコミット後のイベントで行うので、このテストはロールバックしない。
 * データはテストごとの範囲（UUID）で分け、確認するのは自分のデータだけにする。
 */
@IntegrationTest
class MediaApiTest {

    // ============================== 定数 ==============================

    /**
     * 画像の大きさの上限（10MB）
     */
    private static final int MAX_BYTES = 10 * 1024 * 1024;

    /**
     * 対象外の形式のファイルの先頭
     *
     * <p>GIF。
     */
    private static final byte[] GIF = {'G', 'I', 'F', '8', '9', 'a', 1, 0, 1, 0, 0, 0};

    /**
     * PNG の MIME タイプ
     */
    private static final String PNG_TYPE = "image/png";

    /**
     * WebP の MIME タイプ
     */
    private static final String WEBP_TYPE = "image/webp";

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:30。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:30:00Z");

    /**
     * 署名付き URL や配信の URL を呼ぶ HttpClient
     */
    private final HttpClient http = HttpClient.newHttpClient();

    /**
     * メディアのサービスが出したログ
     */
    @RegisterExtension
    final LogCapture logs = LogCapture.of(MediaService.class);

    // ============================== Bean ==============================

    @Autowired
    MediaApi media;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    DSLContext dsl;

    @Autowired
    S3Client s3;

    /**
     * ファイルの置き場所
     *
     * <p>配信のバケットへの書き込み・削除の失敗を起こし、呼び出しを記録するため、本物を包む。
     */
    @MockitoSpyBean
    ObjectStorage storage;

    /**
     * 仮のアセットのリポジトリ
     *
     * <p>仮のアセットの記録・削除の失敗を起こすため、本物をラップする。
     */
    @MockitoSpyBean
    PendingAssetRepository pendingAssets;

    @Autowired
    MutableClock clock;

    @BeforeEach
    void setUp() {
        clock.setInstant(NOW);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("受付")
    class RequestUpload {

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"image/jpeg", "image/png", "image/webp", "image/bmp", "image/heic", "image/heif", "image/avif"})
        @DisplayName("AC-39、67 受け付ける形式なら、申告の形式を付けて PUT する署名付き URL を返す")
        void acceptsSupportedFormats(String contentType) {
            // 操作：受付をする
            var upload = media.requestImageUpload(scope(), contentType, 100);

            // 確認：受付 ID、申告の形式のヘッダー、まだ切れていない期限がある
            assertThat(upload.uploadId()).isNotNull();
            assertThat(upload.headers()).containsEntry("Content-Type", contentType);
            assertThat(upload.expiresAt()).isAfter(Instant.now());
        }

        @Test
        @DisplayName("AC-39 返した署名付き URL へ、ファイルを PUT できる")
        void acceptsPutToSignedUrl() throws Exception {
            // 準備：PNG の受付
            var png = TestImages.png();
            var upload = media.requestImageUpload(scope(), PNG_TYPE, png.length);

            // 操作と確認：返したヘッダーを付けて PUT すると 200
            assertThat(put(upload, png)).isEqualTo(200);
        }

        @Test
        @DisplayName("AC-39 対象外の形式は拒否する")
        void rejectsUnsupportedFormat() {
            // 操作と確認：業務のエラー UNSUPPORTED_IMAGE_FORMAT で拒否される
            assertBusinessError(() -> media.requestImageUpload(scope(), "image/gif", 100), MediaErrorCode.UNSUPPORTED_IMAGE_FORMAT);
        }

        @Test
        @DisplayName("AC-39 0 バイトは拒否する")
        void rejectsEmptySize() {
            // 操作と確認：業務のエラー IMAGE_TOO_LARGE で拒否される
            assertBusinessError(() -> media.requestImageUpload(scope(), PNG_TYPE, 0), MediaErrorCode.IMAGE_TOO_LARGE);
        }

        @Test
        @DisplayName("AC-39 10MB を超える大きさは拒否する")
        void rejectsOverMaxSize() {
            // 操作と確認：上限より 1 バイト大きいと、業務のエラー IMAGE_TOO_LARGE で拒否される
            assertBusinessError(() -> media.requestImageUpload(scope(), PNG_TYPE, MAX_BYTES + 1), MediaErrorCode.IMAGE_TOO_LARGE);
        }

        @Test
        @DisplayName("AC-39 下限の 1 バイトは受け付ける")
        void acceptsMinSize() {
            // 操作と確認：1 バイトは受け付ける
            assertThatCode(() -> media.requestImageUpload(scope(), PNG_TYPE, 1)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("AC-39 ちょうど 10MB は受け付ける")
        void acceptsMaxSize() {
            // 操作と確認：上限と同じ大きさは受け付ける
            assertThatCode(() -> media.requestImageUpload(scope(), PNG_TYPE, MAX_BYTES)).doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"../other", ""})
        @DisplayName("範囲は英数字とハイフンだけ（キーの区切りを含められない）")
        void rejectsInvalidScope(String scope) {
            // 操作と確認：プログラムの誤りとして拒否される
            assertThatThrownBy(() -> media.requestImageUpload(scope, PNG_TYPE, 100))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("用意")
    class Prepare {

        @Test
        @DisplayName("AC-304 用意は、アセットを記録しない")
        void preparesWithoutRecord() throws Exception {
            // 準備：PNG をアップロードする
            var scope = scope();
            var upload = uploaded(scope, PNG_TYPE, TestImages.png());

            // 操作：用意する
            var prepared = media.prepareImage(scope, upload.uploadId());

            // 確認：アセットの記録がない
            assertThat(dsl.fetchExists(ASSET, ASSET.ID.eq(prepared.assetId().value()))).isFalse();

            // 後片付け：配信のファイルを消す
            media.discardImage(prepared);
        }

        @Test
        @DisplayName("DC-489-1 用意すると、アセット ID・配信のキー・用意した時刻の仮のアセットが記録される")
        void recordsPendingAsset() throws Exception {
            // 準備：PNG をアップロードする
            var scope = scope();
            var upload = uploaded(scope, PNG_TYPE, TestImages.png());

            // 操作：用意する
            var prepared = media.prepareImage(scope, upload.uploadId());

            // 確認：配信のキーと用意した時刻を持つ仮のアセットがある
            var pending = dsl.fetchOne(PENDING_ASSET, PENDING_ASSET.ID.eq(prepared.assetId().value()));
            assertThat(pending).isNotNull();
            assertThat(pending.getStorageKey()).isEqualTo("assets/" + prepared.assetId().value() + ".webp");
            assertThat(pending.getPreparedAt()).isEqualTo(NOW);

            // 後片付け：配信のファイルを削除する
            media.discardImage(prepared);
        }

        @Test
        @DisplayName("DC-489-1 配信のバケットに書く時には、仮のアセットの記録がコミットされている")
        void commitsPendingAssetBeforePublishing() throws Exception {
            // 準備：PNG をアップロードし、配信のバケットに書くときに、仮のアセットの記録が別の接続から見えるかを記録する
            // ※別のスレッドで読む（同じスレッドで読むと、開いたままのトランザクションの接続に加わり、コミット前の行も見えるため）
            var scope = scope();
            var upload = uploaded(scope, PNG_TYPE, TestImages.png());
            var committed = new CopyOnWriteArrayList<Boolean>();
            doAnswer(invocation -> {
                String storageKey = invocation.getArgument(0);
                committed.add(CompletableFuture.supplyAsync(() -> dsl.fetchExists(PENDING_ASSET, PENDING_ASSET.STORAGE_KEY.eq(storageKey)))
                        .get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
                return invocation.callRealMethod();
            }).when(storage).putPublished(anyString(), any(), anyString());

            // 操作：用意する
            var prepared = media.prepareImage(scope, upload.uploadId());

            // 確認：書き込みは 1 回で、その時に記録が見える
            assertThat(committed).containsExactly(true);

            // 後片付け：配信のファイルを削除する
            media.discardImage(prepared);
        }

        @Test
        @DisplayName("DC-489-1 仮のアセットを記録できなければ、用意は失敗し、配信のバケットに書かない")
        void doesNotPublishWhenPendingAssetFails() throws Exception {
            // 準備：PNG をアップロードし、仮のアセットの記録を失敗させる
            var scope = scope();
            var upload = uploaded(scope, PNG_TYPE, TestImages.png());
            doThrow(new DataAccessResourceFailureException("connection refused")).when(pendingAssets).insert(any());

            // 操作と確認：記録の例外がそのまま投げられる
            assertThatThrownBy(() -> media.prepareImage(scope, upload.uploadId())).isInstanceOf(DataAccessResourceFailureException.class);

            // 確認：配信のバケットに書いていない
            verify(storage, never()).putPublished(anyString(), any(), anyString());
        }

        @Test
        @DisplayName("AC-304 用意は、ファイル保存のサービスをデータベースのトランザクションの外で呼ぶ")
        void publishesOutsideTransaction() throws Exception {
            // 準備：PNG をアップロードし、配信のバケットに書くときにトランザクションがあるかを記録する
            var scope = scope();
            var upload = uploaded(scope, PNG_TYPE, TestImages.png());
            var transactionActive = new CopyOnWriteArrayList<Boolean>();
            doAnswer(invocation -> {
                transactionActive.add(TransactionSynchronizationManager.isActualTransactionActive());
                return invocation.callRealMethod();
            }).when(storage).putPublished(anyString(), any(), anyString());

            // 操作：トランザクションの外から用意する
            var prepared = media.prepareImage(scope, upload.uploadId());

            // 確認：配信のバケットへの書き込みは 1 回で、トランザクションの外
            assertThat(transactionActive).containsExactly(false);

            // 後片付け：配信のファイルを消す
            media.discardImage(prepared);
        }

        @Test
        @DisplayName("AC-68 用意は、WebP に変換して配信のバケットに置く")
        void publishesWebp() throws Exception {
            // 準備：PNG をアップロードする
            var scope = scope();
            var upload = uploaded(scope, PNG_TYPE, TestImages.png());

            // 操作：用意する
            var prepared = media.prepareImage(scope, upload.uploadId());

            // 確認：配信のバケットに、WebP として置かれている
            var response = getPublished(prepared);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(new String(response.body(), 8, 4, StandardCharsets.US_ASCII)).isEqualTo("WEBP");
            assertThat(response.headers().firstValue("Content-Type")).contains(WEBP_TYPE);

            // 後片付け：配信のファイルを消す
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
            // 準備：その形式の画像をアップロードする
            var scope = scope();
            var upload = uploaded(scope, contentType, TestImages.of(file));

            // 操作：用意して登録する
            var assetId = confirm(scope, upload.uploadId());

            // 確認：配信の URL が WebP を指し、WebP として配信される
            var url = media.urls(List.of(assetId)).get(assetId);
            assertThat(url.toString()).endsWith(".webp");
            assertThat(get(url).headers().firstValue("Content-Type")).contains(WEBP_TYPE);
        }

        @Test
        @DisplayName("AC-39 形式は申告ではなく先頭のバイトで判定する（PNG と申告した JPEG も変換できる）")
        void detectsFormatFromContent() throws Exception {
            // 準備：JPEG を PNG と申告してアップロードする
            var scope = scope();
            var upload = uploaded(scope, PNG_TYPE, TestImages.jpeg());

            // 操作：用意して登録する
            var assetId = confirm(scope, upload.uploadId());

            // 確認：WebP に変換されている
            assertThat(media.urls(List.of(assetId)).get(assetId).toString()).endsWith(".webp");
        }

        @Test
        @DisplayName("AC-70 変換できない（壊れた）ファイルは拒否する")
        void rejectsUnconvertibleFile() throws Exception {
            // 準備：壊れた HEIC をアップロードする
            var scope = scope();
            var upload = uploaded(scope, "image/heic", TestImages.of("broken.heic"));

            // 操作と確認：業務のエラー IMAGE_CONVERSION_FAILED で拒否される
            assertBusinessError(() -> media.prepareImage(scope, upload.uploadId()), MediaErrorCode.IMAGE_CONVERSION_FAILED);
        }

        @Test
        @DisplayName("DC-489-1 配信のバケットに書けなければ、用意は失敗し、仮のアセットが残る")
        void failsWhenPublishingFails() throws Exception {
            // 準備：PNG をアップロードし、書こうとした配信のキーを記録してから、配信のバケットへの書き込みを失敗させる
            var scope = scope();
            var upload = uploaded(scope, PNG_TYPE, TestImages.png());
            var storageKeys = new CopyOnWriteArrayList<String>();
            doAnswer(invocation -> {
                storageKeys.add(invocation.getArgument(0));
                throw S3Exception.builder().statusCode(404).message("The specified bucket does not exist").build();
            }).when(storage).putPublished(anyString(), any(), anyString());

            // 操作と確認：書き込みの例外がそのまま投げられる
            assertThatThrownBy(() -> media.prepareImage(scope, upload.uploadId())).isInstanceOf(S3Exception.class);

            // 確認：書こうとしたキーの仮のアセットが残る（失敗でもファイルが置かれていることがあり、片付けのジョブが削除する）
            assertThat(storageKeys).hasSize(1);
            assertThat(dsl.fetchExists(PENDING_ASSET, PENDING_ASSET.STORAGE_KEY.eq(storageKeys.getFirst()))).isTrue();
        }

        @Test
        @DisplayName("AC-39 中身が対象の画像でないファイルは拒否する")
        void rejectsInvalidFile() throws Exception {
            // 準備：GIF を PNG と申告してアップロードする
            var scope = scope();
            var upload = uploaded(scope, PNG_TYPE, GIF);

            // 操作と確認：業務のエラー INVALID_IMAGE_FILE で拒否される
            assertBusinessError(() -> media.prepareImage(scope, upload.uploadId()), MediaErrorCode.INVALID_IMAGE_FILE);
        }

        @Test
        @DisplayName("AC-39 アップロードしたファイルが 10MB を超えていれば拒否する（署名付き URL を通さずに置かれたもの）")
        void rejectsOversizedFile() {
            // 準備：PNG で始まる、上限より 1 バイト大きいファイルを、アップロードのバケットに直接置く
            var scope = scope();
            var uploadId = new UploadId(UUID.randomUUID());
            var body = new byte[MAX_BYTES + 1];
            var png = TestImages.png();
            System.arraycopy(png, 0, body, 0, png.length);
            s3.putObject(request -> request.bucket(TestcontainersConfig.INCOMING_BUCKET).key(scope + "/" + uploadId.value()),
                    RequestBody.fromBytes(body));

            // 操作と確認：業務のエラー IMAGE_TOO_LARGE で拒否される
            assertBusinessError(() -> media.prepareImage(scope, uploadId), MediaErrorCode.IMAGE_TOO_LARGE);
        }

        @Test
        @DisplayName("アップロードのない uploadId は拒否する")
        void rejectsUnknownUploadId() {
            // 操作と確認：業務のエラー IMAGE_UPLOAD_NOT_FOUND で拒否される
            assertBusinessError(() -> media.prepareImage(scope(), new UploadId(UUID.randomUUID())), MediaErrorCode.IMAGE_UPLOAD_NOT_FOUND);
        }

        @Test
        @DisplayName("別の範囲のアップロードは拒否する")
        void rejectsUploadOfOtherScope() throws Exception {
            // 準備：ある範囲でアップロードする
            var upload = uploaded(scope(), PNG_TYPE, TestImages.png());

            // 操作と確認：別の範囲から用意すると、業務のエラー IMAGE_UPLOAD_NOT_FOUND で拒否される
            assertBusinessError(() -> media.prepareImage(scope(), upload.uploadId()), MediaErrorCode.IMAGE_UPLOAD_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("登録")
    class Register {

        @Test
        @DisplayName("AC-37、68 用意した画像を登録すると、WebP のアセットが記録される")
        void recordsAsset() throws Exception {
            // 準備：PNG をアップロードする
            var scope = scope();
            var upload = uploaded(scope, PNG_TYPE, TestImages.png());

            // 操作：用意して登録する
            var assetId = confirm(scope, upload.uploadId());

            // 確認：形式が WebP のアセットが記録されている
            assertThat(dsl.fetchExists(ASSET, ASSET.ID.eq(assetId.value()))).isTrue();
            assertThat(dsl.fetchValue(ASSET.FORMAT, ASSET.ID.eq(assetId.value()))).isEqualTo("WEBP");
        }

        @Test
        @DisplayName("AC-37、68 登録した画像は、アセット ID の配信の URL で取得できる")
        void servesRegisteredAsset() throws Exception {
            // 準備：PNG をアップロードする
            var scope = scope();
            var upload = uploaded(scope, PNG_TYPE, TestImages.png());

            // 操作：用意して登録する
            var assetId = confirm(scope, upload.uploadId());

            // 確認：配信の URL がアセット ID の WebP を指し、取得できる
            var url = media.urls(List.of(assetId)).get(assetId);
            assertThat(url.toString()).endsWith("/assets/" + assetId.value() + ".webp");
            assertThat(get(url).statusCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("AC-304 登録は呼んだ側のトランザクションの中で行い、トランザクションがなければ失敗する")
        void requiresTransaction() throws Exception {
            // 準備：PNG を用意する
            var scope = scope();
            var prepared = media.prepareImage(scope, uploaded(scope, PNG_TYPE, TestImages.png()).uploadId());

            // 操作と確認：トランザクションの外で登録すると失敗する
            assertThatThrownBy(() -> media.registerImage(prepared)).isInstanceOf(IllegalTransactionStateException.class);

            // 確認：アセットは記録されない
            assertThat(dsl.fetchExists(ASSET, ASSET.ID.eq(prepared.assetId().value()))).isFalse();

            // 後片付け：配信のファイルを消す
            media.discardImage(prepared);
        }

        @Test
        @DisplayName("呼んだ側のトランザクションが取り消されると、アセットの記録も残らない")
        void rollsBackWithCaller() throws Exception {
            // 準備：PNG を用意する
            var scope = scope();
            var prepared = media.prepareImage(scope, uploaded(scope, PNG_TYPE, TestImages.png()).uploadId());

            // 操作：登録したトランザクションを取り消す
            transactionTemplate.executeWithoutResult(status -> {
                media.registerImage(prepared);
                status.setRollbackOnly();
            });

            // 確認：アセットの記録も、配信の URL も残らない
            assertThat(dsl.fetchExists(ASSET, ASSET.ID.eq(prepared.assetId().value()))).isFalse();
            assertThat(media.urls(List.of(prepared.assetId()))).isEmpty();

            // 後片付け：配信のファイルを消す
            media.discardImage(prepared);
        }

        @Test
        @DisplayName("DC-489-2 登録すると、仮のアセットの記録が削除される")
        void deletesPendingAsset() throws Exception {
            // 準備：PNG をアップロードする
            var scope = scope();
            var upload = uploaded(scope, PNG_TYPE, TestImages.png());

            // 操作：用意して登録する
            var assetId = confirm(scope, upload.uploadId());

            // 確認：仮のアセットの記録がない
            assertThat(pendingExists(assetId)).isFalse();
        }

        @Test
        @DisplayName("DC-489-2 呼んだ側のトランザクションが取り消されると、仮のアセットの記録が残る")
        void keepsPendingAssetOnRollback() throws Exception {
            // 準備：PNG を用意する
            var scope = scope();
            var prepared = media.prepareImage(scope, uploaded(scope, PNG_TYPE, TestImages.png()).uploadId());

            // 操作：登録したトランザクションを取り消す
            transactionTemplate.executeWithoutResult(status -> {
                media.registerImage(prepared);
                status.setRollbackOnly();
            });

            // 確認：仮のアセットの記録が残る
            assertThat(pendingExists(prepared.assetId())).isTrue();

            // 後片付け：配信のファイルを削除する
            media.discardImage(prepared);
        }

        @Test
        @DisplayName("DC-489-5 仮のアセットの記録がない画像は登録できず、呼んだ側のトランザクションも取り消される")
        void rejectsWithoutPendingAsset() throws Exception {
            // 準備：PNG を用意し、仮のアセットの記録を削除する（片付けのジョブが削除した後）
            var scope = scope();
            var prepared = media.prepareImage(scope, uploaded(scope, PNG_TYPE, TestImages.png()).uploadId());
            dsl.deleteFrom(PENDING_ASSET).where(PENDING_ASSET.ID.eq(prepared.assetId().value())).execute();
            var callerAssetId = UUID.randomUUID();

            // 操作と確認：呼んだ側が別のアセットを記録してから登録すると、例外になる
            assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                dsl.insertInto(ASSET)
                        .set(ASSET.ID, callerAssetId)
                        .set(ASSET.FORMAT, "WEBP")
                        .set(ASSET.STORAGE_KEY, "assets/" + callerAssetId + ".webp")
                        .set(ASSET.CREATED_AT, NOW)
                        .execute();
                media.registerImage(prepared);
            })).isInstanceOf(IllegalStateException.class);

            // 確認：用意した画像のアセットも、呼んだ側が記録したアセットも残らない
            assertThat(dsl.fetchExists(ASSET, ASSET.ID.eq(prepared.assetId().value()))).isFalse();
            assertThat(dsl.fetchExists(ASSET, ASSET.ID.eq(callerAssetId))).isFalse();

            // 後片付け：配信のファイルを削除する
            media.discardImage(prepared);
        }
    }

    @Nested
    @DisplayName("破棄")
    class Discard {

        @Test
        @DisplayName("AC-305 破棄すると、用意で置いた配信のファイルが消える")
        void discards() throws Exception {
            // 準備：PNG を用意する
            var scope = scope();
            var prepared = media.prepareImage(scope, uploaded(scope, PNG_TYPE, TestImages.png()).uploadId());

            // 操作：破棄する
            media.discardImage(prepared);

            // 確認：配信のファイルがない
            assertThat(getPublished(prepared).statusCode()).isEqualTo(404);
        }

        @Test
        @DisplayName("AC-305 消すのに失敗しても例外を投げない")
        void doesNotThrowWhenDeleteFails() throws Exception {
            // 準備：PNG を用意し、配信のファイルの削除を失敗させる
            var scope = scope();
            var prepared = media.prepareImage(scope, uploaded(scope, PNG_TYPE, TestImages.png()).uploadId());
            doThrow(SdkClientException.create("connection refused")).when(storage).deletePublished(anyString());

            // 操作と確認：破棄は例外を投げない
            assertThatCode(() -> media.discardImage(prepared)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("AC-305 消すのに失敗したら、警告のログに保存先のキーを出す")
        void logsWhenDeleteFails() throws Exception {
            // 準備：PNG を用意し、配信のファイルの削除を失敗させる
            var scope = scope();
            var prepared = media.prepareImage(scope, uploaded(scope, PNG_TYPE, TestImages.png()).uploadId());
            doThrow(SdkClientException.create("connection refused")).when(storage).deletePublished(anyString());

            // 操作：破棄する
            logs.clear();
            media.discardImage(prepared);

            // 確認：この破棄の後に、保存先のキーを含む警告のログが出ている
            assertThat(logs.messages(Level.WARN)).anyMatch(message -> message.contains("assets/" + prepared.assetId().value() + ".webp"));
        }

        @Test
        @DisplayName("DC-489-3 破棄すると、配信のファイルと仮のアセットの記録が削除される")
        void deletesPendingAsset() throws Exception {
            // 準備：PNG を用意する
            var scope = scope();
            var prepared = media.prepareImage(scope, uploaded(scope, PNG_TYPE, TestImages.png()).uploadId());

            // 操作：破棄する
            media.discardImage(prepared);

            // 確認：配信のファイルも、仮のアセットの記録もない
            assertThat(getPublished(prepared).statusCode()).isEqualTo(404);
            assertThat(pendingExists(prepared.assetId())).isFalse();
        }

        @Test
        @DisplayName("DC-489-3 配信のファイルを削除できなければ、仮のアセットの記録が残る")
        void keepsPendingAssetWhenDeleteFails() throws Exception {
            // 準備：PNG を用意し、配信のファイルの削除を失敗させる
            var scope = scope();
            var prepared = media.prepareImage(scope, uploaded(scope, PNG_TYPE, TestImages.png()).uploadId());
            doThrow(SdkClientException.create("connection refused")).when(storage).deletePublished(anyString());

            // 操作：破棄する
            media.discardImage(prepared);

            // 確認：仮のアセットの記録が残り、削除しなかった
            assertThat(pendingExists(prepared.assetId())).isTrue();
            verify(pendingAssets, never()).deleteById(any());
        }

        @Test
        @DisplayName("DC-489-3 仮のアセットの記録を削除できなくても例外を投げず、警告のログに配信のキーを出す")
        void logsWhenPendingAssetDeleteFails() throws Exception {
            // 準備：PNG を用意し、仮のアセットの記録の削除を失敗させる
            var scope = scope();
            var prepared = media.prepareImage(scope, uploaded(scope, PNG_TYPE, TestImages.png()).uploadId());
            doThrow(new DataAccessResourceFailureException("connection refused")).when(pendingAssets).deleteById(any());

            // 操作と確認：破棄は例外を投げない
            logs.clear();
            assertThatCode(() -> media.discardImage(prepared)).doesNotThrowAnyException();

            // 確認：この破棄の後に、配信のキーを含む警告のログが出ている
            assertThat(logs.messages(Level.WARN)).anyMatch(message -> message.contains("assets/" + prepared.assetId().value() + ".webp"));
        }
    }

    @Nested
    @DisplayName("トランザクションの前提")
    class TransactionPrecondition {

        @Test
        @DisplayName("画像の用意は、トランザクションの中から呼ぶと失敗する")
        void prepareFailsInsideTransaction() throws Exception {
            // 準備：PNG をアップロードする
            var scope = scope();
            var upload = uploaded(scope, PNG_TYPE, TestImages.png());

            // 操作と確認：トランザクションの中で用意すると失敗する
            assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> media.prepareImage(scope, upload.uploadId())))
                    .isInstanceOf(IllegalTransactionStateException.class);

            // 確認：配信のバケットにファイルを置いていない
            verify(storage, never()).putPublished(anyString(), any(), anyString());
        }

        @Test
        @DisplayName("画像の破棄は、トランザクションの中から呼ぶと失敗する")
        void discardFailsInsideTransaction() throws Exception {
            // 準備：PNG を用意する
            var scope = scope();
            var prepared = media.prepareImage(scope, uploaded(scope, PNG_TYPE, TestImages.png()).uploadId());

            // 操作と確認：トランザクションの中で破棄すると失敗する
            assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> media.discardImage(prepared)))
                    .isInstanceOf(IllegalTransactionStateException.class);

            // 確認：配信のファイルが残っている
            assertThat(getPublished(prepared).statusCode()).isEqualTo(200);

            // 後片付け：配信のファイルを削除する
            media.discardImage(prepared);
        }
    }

    @Nested
    @DisplayName("解放")
    class Release {

        @Test
        @DisplayName("手放すと、アセットの記録が消え、配信の URL を引けなくなる")
        void removesRecord() throws Exception {
            // 準備：登録したアセット
            var assetId = confirmed();

            // 操作：トランザクションの中で手放す
            transactionTemplate.executeWithoutResult(status -> media.release(List.of(assetId)));

            // 確認：記録も、配信の URL も残らない
            assertThat(dsl.fetchExists(ASSET, ASSET.ID.eq(assetId.value()))).isFalse();
            assertThat(media.urls(List.of(assetId))).isEmpty();
        }

        @Test
        @DisplayName("手放すと、コミットの後に配信のファイルが消える")
        void deletesFileAfterCommit() throws Exception {
            // 準備：登録したアセットと、その配信の URL
            var assetId = confirmed();
            var url = media.urls(List.of(assetId)).get(assetId);

            // 操作：トランザクションの中で手放す
            transactionTemplate.executeWithoutResult(status -> media.release(List.of(assetId)));

            // 確認：ファイルを消すイベントが、手放したトランザクションで登録されている
            assertThat(releaseRegistered(assetId)).isTrue();

            // 確認：コミットの後に、配信のファイルが消える
            await().atMost(TIMEOUT).untilAsserted(() -> assertThat(get(url).statusCode()).isEqualTo(404));
        }

        @Test
        @DisplayName("AC-306 ファイルを消すリスナーは、データベースのトランザクションの外でファイル保存を呼ぶ")
        void deletesOutsideTransaction() throws Exception {
            // 準備：登録したアセットと、配信のファイルを消すときにトランザクションがあるかの記録
            var assetId = confirmed();
            var transactionActive = new CopyOnWriteArrayList<Boolean>();
            doAnswer(invocation -> {
                transactionActive.add(TransactionSynchronizationManager.isActualTransactionActive());
                return invocation.callRealMethod();
            }).when(storage).deletePublished(anyString());

            // 操作：トランザクションの中で手放す
            transactionTemplate.executeWithoutResult(status -> media.release(List.of(assetId)));

            // 確認：リスナーの削除は 1 回で、トランザクションの外
            await().atMost(TIMEOUT).until(() -> !transactionActive.isEmpty());
            assertThat(transactionActive).containsExactly(false);
        }

        @Test
        @DisplayName("呼んだ側のトランザクションが取り消されると、記録もファイルも残る")
        void keepsAssetOnRollback() throws Exception {
            // 準備：登録したアセットと、その配信の URL
            var assetId = confirmed();
            var url = media.urls(List.of(assetId)).get(assetId);

            // 操作：手放したトランザクションを取り消す
            transactionTemplate.executeWithoutResult(status -> {
                media.release(List.of(assetId));
                status.setRollbackOnly();
            });

            // 確認：記録が残る
            assertThat(dsl.fetchExists(ASSET, ASSET.ID.eq(assetId.value()))).isTrue();

            // 確認：ファイルを消すイベントが登録されず、配信のファイルが残る（登録がないので、待たずに確認できる）
            assertThat(releaseRegistered(assetId)).isFalse();
            assertThat(get(url).statusCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("すでにないアセットを手放しても成功する")
        void ignoresReleasedAsset() throws Exception {
            // 準備：登録して、手放したアセット
            var assetId = confirmed();
            transactionTemplate.executeWithoutResult(status -> media.release(List.of(assetId)));

            // 操作と確認：手放したアセットと、ないアセットを手放しても例外にならない
            assertThatCode(() -> transactionTemplate.executeWithoutResult(status ->
                    media.release(List.of(assetId, new AssetId(UUID.randomUUID()))))).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("配信の URL")
    class Urls {

        @Test
        @DisplayName("複数のアセットの URL をまとめて返し、ないアセットは含めない")
        void returnsUrlsInBatch() throws Exception {
            // 準備：登録した 2 つのアセットと、ないアセット ID
            var first = confirmed();
            var second = confirmed();
            var unknown = new AssetId(UUID.randomUUID());

            // 操作：まとめて URL を引く
            var urls = media.urls(List.of(first, second, unknown));

            // 確認：登録した 2 つだけ
            assertThat(urls.keySet()).isEqualTo(Set.of(first, second));
        }

        @Test
        @DisplayName("アセット ID がなければ、空を返す")
        void returnsEmptyForNoIds() {
            // 操作と確認：空
            assertThat(media.urls(List.of())).isEmpty();
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * アセット解放のイベントが、イベント発行記録に登録されたかを返す。
     *
     * @param assetId アセット ID
     * @return 登録されていれば true
     */
    private boolean releaseRegistered(AssetId assetId) {
        return dsl.fetchExists(EVENT_PUBLICATION, EVENT_PUBLICATION.EVENT_TYPE.endsWith(".AssetReleased")
                .and(EVENT_PUBLICATION.SERIALIZED_EVENT.contains(assetId.value().toString())));
    }

    /**
     * 仮のアセットの記録があるかを返す。
     *
     * @param assetId アセット ID
     * @return あれば true
     */
    private boolean pendingExists(AssetId assetId) {
        return dsl.fetchExists(PENDING_ASSET, PENDING_ASSET.ID.eq(assetId.value()));
    }

    // ============================== 準備の補助 ==============================

    /**
     * テストごとに別の範囲（アップロードの持ち主）を作る。
     *
     * @return 範囲
     */
    private static String scope() {
        return UUID.randomUUID().toString();
    }

    /**
     * 受付をして、ファイルを署名付き URL へ PUT する。
     *
     * @param scope       範囲
     * @param contentType 申告する形式
     * @param body        ファイルの中身
     * @return 受付
     * @throws Exception PUT できない
     */
    private ImageUpload uploaded(String scope, String contentType, byte[] body) throws Exception {
        var upload = media.requestImageUpload(scope, contentType, body.length);
        assertThat(put(upload, body)).isEqualTo(200);
        return upload;
    }

    /**
     * 受け付けた画像を用意し、トランザクションの中で登録する。
     *
     * @param scope    範囲
     * @param uploadId 受付 ID
     * @return アセット ID
     */
    private AssetId confirm(String scope, UploadId uploadId) {
        var prepared = media.prepareImage(scope, uploadId);
        transactionTemplate.executeWithoutResult(status -> media.registerImage(prepared));
        return prepared.assetId();
    }

    /**
     * PNG を受け付けて用意・登録する。
     *
     * @return アセット ID
     * @throws Exception PUT できない
     */
    private AssetId confirmed() throws Exception {
        var scope = scope();
        return confirm(scope, uploaded(scope, PNG_TYPE, TestImages.png()).uploadId());
    }

    /**
     * 署名付き URL へ、受付のヘッダーを付けてファイルを PUT する。
     *
     * @param upload 受付
     * @param body   ファイルの中身
     * @return HTTP ステータス
     * @throws IOException          送れない
     * @throws InterruptedException 待つ間に割り込まれた
     */
    private int put(ImageUpload upload, byte[] body) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(upload.uploadUrl()).PUT(HttpRequest.BodyPublishers.ofByteArray(body));
        upload.headers().forEach(request::header);
        return http.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    /**
     * 配信の URL を、認証なしで取得する。
     *
     * @param url 配信の URL
     * @return 応答
     * @throws IOException          送れない
     * @throws InterruptedException 待つ間に割り込まれた
     */
    private HttpResponse<byte[]> get(URI url) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(url).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    /**
     * 用意した画像の配信のファイルを、記録を通さずに取得する。
     *
     * <p>登録の前は、配信の URL を引けないため。
     *
     * @param prepared 用意した画像
     * @return 応答
     * @throws IOException          送れない
     * @throws InterruptedException 待つ間に割り込まれた
     */
    private HttpResponse<byte[]> getPublished(PreparedImage prepared) throws IOException, InterruptedException {
        return get(storage.publicUrl("assets/" + prepared.assetId().value() + ".webp"));
    }
}

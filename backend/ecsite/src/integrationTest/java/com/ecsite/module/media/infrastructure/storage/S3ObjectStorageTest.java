package com.ecsite.module.media.infrastructure.storage;

import com.ecsite.module.media.application.model.PresignedUpload;
import com.ecsite.module.media.application.port.ObjectStorage;
import com.ecsite.support.config.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner;
import software.amazon.awssdk.http.auth.spi.signer.HttpSigner;
import software.amazon.awssdk.identity.spi.AwsCredentialsIdentity;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S3 のファイルの置き場所の統合テスト
 *
 * <p>Testcontainers の SeaweedFS（資格情報を検証する設定）に対して、署名付き URL での PUT から、配信のバケットへの書き込み・削除までを確認する。
 * キーはテストごとに UUID で分ける。
 */
@IntegrationTest
class S3ObjectStorageTest {

    // ============================== 定数 ==============================

    /**
     * アップロードするファイル
     *
     * <p>PNG の印の後に 4 バイトが続く。
     */
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2, 3, 4};

    /**
     * PNG の MIME タイプ
     */
    private static final String PNG_TYPE = "image/png";

    /**
     * 配信のバケットに書くファイル
     */
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 4, 0, 0, 0, 'W', 'E', 'B', 'P'};

    /**
     * WebP の MIME タイプ
     */
    private static final String WEBP_TYPE = "image/webp";

    /**
     * 配信のファイルに付けるキャッシュの指定
     *
     * <p>キーを上書きしないので、1 年間キャッシュさせ、中身が変わらないことを示す。
     */
    private static final String PUBLIC_CACHE_CONTROL = "public, max-age=31536000, immutable";

    /**
     * 期限を確認するときの、署名付き URL の有効期間
     */
    private static final Duration SHORT_TTL = Duration.ofMinutes(1);

    /**
     * 署名付き URL や配信の URL を呼ぶ HttpClient
     */
    private final HttpClient http = HttpClient.newHttpClient();

    // ============================== Bean ==============================

    @Autowired
    ObjectStorage storage;

    @Autowired
    S3Client s3;

    @Autowired
    S3Presigner presigner;

    @Autowired
    MediaStorageProperties properties;

    // ============================== テスト ==============================

    @Nested
    @DisplayName("アップロード")
    class Upload {

        @Test
        @DisplayName("署名付き URL は、申告の形式のヘッダーと、まだ切れていない期限を持つ")
        void presignsWithHeadersAndExpiry() {
            // 操作：署名付き URL を発行する
            var upload = storage.presignUpload(uploadKey(), PNG_TYPE, PNG.length);

            // 確認：形式のヘッダーと、未来の期限
            assertThat(upload.headers()).containsEntry("Content-Type", PNG_TYPE);
            assertThat(upload.expiresAt()).isAfter(Instant.now());
        }

        @Test
        @DisplayName("署名付き URL でアップロードのバケットへ PUT できる")
        void uploadsWithPresignedUrl() throws Exception {
            // 準備：署名付き URL を発行する
            var upload = storage.presignUpload(uploadKey(), PNG_TYPE, PNG.length);

            // 操作と確認：PUT すると 200
            assertThat(put(upload, PNG)).isEqualTo(200);
        }

        @Test
        @DisplayName("PUT したファイルの大きさを読める")
        void readsUploadSize() throws Exception {
            // 準備：PUT したファイル
            var key = uploadedKey();

            // 操作と確認：PUT した大きさ
            assertThat(storage.findUploadSize(key)).contains((long) PNG.length);
        }

        @Test
        @DisplayName("PUT したファイルの先頭を、指定の長さだけ読める")
        void readsUploadHead() throws Exception {
            // 準備：PUT したファイル
            var key = uploadedKey();

            // 操作と確認：先頭の 4 バイト
            assertThat(storage.readUploadHead(key, 4)).containsExactly((byte) 0x89, 'P', 'N', 'G');
        }

        @Test
        @DisplayName("ファイルより長く指定すると、ファイルの全体を読む")
        void readsWholeUploadWhenShorter() throws Exception {
            // 準備：PUT したファイル
            var key = uploadedKey();

            // 操作と確認：ファイルの全体
            assertThat(storage.readUploadHead(key, 100)).containsExactly(PNG);
        }

        @Test
        @DisplayName("アップロードのないキーは、大きさが空になる")
        void returnsEmptyForMissingUpload() {
            // 操作と確認：空
            assertThat(storage.findUploadSize(uploadKey())).isEmpty();
        }
    }

    @Nested
    @DisplayName("署名付き URL の検証")
    class PresignedUrlValidation {

        @Test
        @DisplayName("署名を書き換えた URL では PUT できない")
        void rejectsTamperedSignature() throws Exception {
            // 準備：署名の先頭を書き換えた URL
            var key = uploadKey();
            var upload = storage.presignUpload(key, PNG_TYPE, PNG.length);
            var tampered = upload.url().toString().replaceFirst("X-Amz-Signature=[0-9a-f]{4}", "X-Amz-Signature=0000");

            // 操作と確認：PUT すると 403
            assertThat(put(new PresignedUpload(URI.create(tampered), upload.headers(), upload.expiresAt()), PNG))
                    .isEqualTo(403);

            // 確認：ファイルは置かれていない
            assertThat(storage.findUploadSize(key)).isEmpty();
        }

        @Test
        @DisplayName("キーを別のものに書き換えた URL では PUT できない")
        void rejectsOtherKey() throws Exception {
            // 準備：キーを書き換えた URL
            var key = uploadKey();
            var otherKey = uploadKey();
            var upload = storage.presignUpload(key, PNG_TYPE, PNG.length);
            var rewritten = upload.url().toString().replace(key, otherKey);

            // 操作と確認：PUT すると 403
            assertThat(put(new PresignedUpload(URI.create(rewritten), upload.headers(), upload.expiresAt()), PNG))
                    .isEqualTo(403);

            // 確認：どちらのキーにもファイルは置かれていない
            assertThat(storage.findUploadSize(key)).isEmpty();
            assertThat(storage.findUploadSize(otherKey)).isEmpty();
        }

        @Test
        @DisplayName("URL の有効期間は、設定のとおり")
        void usesConfiguredTtl() {
            // 準備：有効期間を 1 分にした置き場所
            var shortLived = new S3ObjectStorage(s3, presigner, propertiesWithTtl(SHORT_TTL));

            // 操作：署名付き URL を発行する
            var upload = shortLived.presignUpload(uploadKey(), PNG_TYPE, PNG.length);

            // 確認：URL の有効期間が 60 秒
            assertThat(upload.url().getQuery()).contains("X-Amz-Expires=" + SHORT_TTL.toSeconds());
        }

        @Test
        @DisplayName("期限の切れた URL では PUT できない")
        void rejectsExpiredUrl() throws Exception {
            // 準備：今の時計（期限が切れたかの判定は置き場所が行う。時間を待たないよう、署名の時計をずらして、作った時点で期限の切れた URL を作る）
            var now = Clock.systemUTC();

            // 確認（前提）：同じ作り方で今の時刻に署名した URL は通る（署名そのものは正しい）
            assertThat(putSignedAt(now, uploadKey())).isEqualTo(200);

            // 操作と確認：10 分前に署名した（期限の 1 分を過ぎた）URL は 403
            assertThat(putSignedAt(Clock.offset(now, Duration.ofMinutes(-10)), uploadKey())).isEqualTo(403);
        }
    }

    @Nested
    @DisplayName("配信のバケット")
    class Published {

        @Test
        @DisplayName("配信のバケットへ書き込むと、認証なしで読める")
        void putsPublishedReadableByAnyone() throws Exception {
            // 準備：テストごとの配信のキー
            var publicKey = publicKey();

            // 操作：書き込む
            storage.putPublished(publicKey, WEBP, WEBP_TYPE);

            // 確認：配信の URL から、書いた中身を取得できる
            var response = get(storage.publicUrl(publicKey));
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).containsExactly(WEBP);
        }

        @Test
        @DisplayName("配信のファイルには、形式と長いキャッシュの指定が付く")
        void putsPublishedWithHeaders() throws Exception {
            // 準備：テストごとの配信のキー
            var publicKey = publicKey();

            // 操作：書き込む
            storage.putPublished(publicKey, WEBP, WEBP_TYPE);

            // 確認：応答の形式とキャッシュの指定
            var response = get(storage.publicUrl(publicKey));
            assertThat(response.headers().firstValue("Content-Type")).contains(WEBP_TYPE);
            assertThat(response.headers().firstValue("Cache-Control")).contains(PUBLIC_CACHE_CONTROL);
        }

        @Test
        @DisplayName("配信の URL は、配信の基準 URL の下のキー")
        void buildsPublicUrl() {
            // 準備：テストごとの配信のキー
            var publicKey = publicKey();

            // 操作と確認：基準 URL とキーをつないだもの
            assertThat(storage.publicUrl(publicKey).toString()).isEqualTo(properties.publicBaseUrl() + "/" + publicKey);
        }

        @Test
        @DisplayName("配信のファイルを消せる")
        void deletesPublished() {
            // 準備：書き込んだ配信のファイル
            var publicKey = publicKey();
            storage.putPublished(publicKey, WEBP, WEBP_TYPE);

            // 操作：消す
            storage.deletePublished(publicKey);

            // 確認：ファイルがない
            assertThat(existsPublished(publicKey)).isFalse();
        }

        @Test
        @DisplayName("ないファイルを消しても成功する")
        void deletesMissingPublished() {
            // 準備：書き込んで消した配信のファイル
            var publicKey = publicKey();
            storage.putPublished(publicKey, WEBP, WEBP_TYPE);
            storage.deletePublished(publicKey);

            // 操作と確認：もう一度消しても例外にならない
            assertThatCode(() -> storage.deletePublished(publicKey)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("配信のバケットに書けなければ（書く権限がない）、例外になる")
        void failsWhenPublicBucketIsNotWritable() {
            // 準備：アップロードのバケットを読む権限だけを持つ、imgproxy の資格情報のクライアント（seaweedfs/s3.json）
            try (var readOnly = S3Client.builder()
                    .endpointOverride(properties.endpoint())
                    .region(Region.of(properties.region()))
                    .forcePathStyle(true)
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("integration-test-imgproxy", "integration-test-imgproxy-secret")))
                    .build()) {
                var broken = new S3ObjectStorage(readOnly, presigner, properties);

                // 操作と確認：403 の S3 の例外
                assertThatThrownBy(() -> broken.putPublished(publicKey(), WEBP, WEBP_TYPE))
                        .isInstanceOfSatisfying(S3Exception.class, ex -> assertThat(ex.statusCode()).isEqualTo(403));
            }
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 配信のバケットに、そのキーのファイルがあるかを返す。
     *
     * @param publicKey 配信のキー
     * @return あれば true
     */
    private boolean existsPublished(String publicKey) {
        return !s3.listObjectsV2(request -> request.bucket(properties.publicBucket()).prefix(publicKey)).contents().isEmpty();
    }

    // ============================== 準備の補助 ==============================

    /**
     * テストごとに別の、アップロードのバケットのキーを作る。
     *
     * <p>{範囲}/{受付 ID} の形。
     *
     * @return アップロードのキー
     */
    private static String uploadKey() {
        return UUID.randomUUID() + "/" + UUID.randomUUID();
    }

    /**
     * テストごとに別の、配信のキーを作る。
     *
     * @return 配信のキー
     */
    private static String publicKey() {
        return "assets/%s.webp".formatted(UUID.randomUUID());
    }

    /**
     * 署名付き URL で PNG を PUT したキーを返す。
     *
     * @return アップロードのキー
     * @throws Exception PUT できない
     */
    private String uploadedKey() throws Exception {
        var key = uploadKey();
        var upload = storage.presignUpload(key, PNG_TYPE, PNG.length);
        assertThat(put(upload, PNG)).isEqualTo(200);
        return key;
    }

    /**
     * アプリの設定から、署名付き URL の有効期間だけを替えた設定を作る。
     *
     * @param ttl 署名付き URL の有効期間
     * @return 設定
     */
    private MediaStorageProperties propertiesWithTtl(Duration ttl) {
        return new MediaStorageProperties(properties.endpoint(), properties.region(),
                properties.pathStyleAccess(), properties.accessKeyId(), properties.secretAccessKey(),
                properties.incomingBucket(), properties.publicBucket(), properties.publicBaseUrl(),
                ttl, properties.attemptTimeout(), properties.callTimeout());
    }

    /**
     * アップロードのバケットへの PUT の署名付き URL（有効期間 1 分）を、指定の時計の時刻で署名して作り、PUT する。
     *
     * <p>アプリの署名者は署名の時刻を変えられないので、同じ方式（SigV4、クエリに署名）の署名者を直接使う。
     *
     * @param clock     署名の時刻を取る時計
     * @param uploadKey アップロードのキー
     * @return HTTP ステータス
     * @throws IOException          送れない
     * @throws InterruptedException 待つ間に割り込まれた
     */
    private int putSignedAt(Clock clock, String uploadKey) throws IOException, InterruptedException {
        var unsigned = SdkHttpRequest.builder()
                .method(SdkHttpMethod.PUT)
                .uri(URI.create(properties.endpoint() + "/" + properties.incomingBucket() + "/" + uploadKey))
                .putHeader("Content-Type", PNG_TYPE)
                .build();
        var signed = AwsV4HttpSigner.create().sign(request -> request
                .identity(AwsCredentialsIdentity.create(properties.accessKeyId(), properties.secretAccessKey()))
                .request(unsigned)
                .putProperty(AwsV4HttpSigner.SERVICE_SIGNING_NAME, "s3")
                .putProperty(AwsV4HttpSigner.REGION_NAME, properties.region())
                .putProperty(AwsV4HttpSigner.AUTH_LOCATION, AwsV4HttpSigner.AuthLocation.QUERY_STRING)
                .putProperty(AwsV4HttpSigner.EXPIRATION_DURATION, SHORT_TTL)
                .putProperty(AwsV4HttpSigner.PAYLOAD_SIGNING_ENABLED, false)
                .putProperty(HttpSigner.SIGNING_CLOCK, clock));
        var request = HttpRequest.newBuilder(signed.request().getUri())
                .header("Content-Type", PNG_TYPE)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(PNG));
        return http.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    /**
     * 署名付き URL へ、指定のヘッダーを付けてファイルを PUT する。
     *
     * @param upload 署名付き URL とヘッダー
     * @param body   ファイルの中身
     * @return HTTP ステータス
     * @throws IOException          送れない
     * @throws InterruptedException 待つ間に割り込まれた
     */
    private int put(PresignedUpload upload, byte[] body) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(upload.url()).PUT(HttpRequest.BodyPublishers.ofByteArray(body));
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
}

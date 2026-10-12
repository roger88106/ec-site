package com.ecsite.module.media.infrastructure;

import com.ecsite.IntegrationTest;
import com.ecsite.module.media.service.ObjectStorage;
import org.junit.jupiter.api.DisplayName;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ファイルの置き場所の S3 の実装の統合テスト（T1016、T1027、T1037。plan 001 第 7 節）。<br />
 * Testcontainers の SeaweedFS（資格情報を検証する設定）に対して、署名付き URL での PUT から、配信のバケットへの書き込み・削除までを確かめる。
 */
@IntegrationTest
class S3ObjectStorageTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2, 3, 4};

    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 4, 0, 0, 0, 'W', 'E', 'B', 'P'};

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    ObjectStorage storage;

    @Autowired
    S3Client s3;

    @Autowired
    S3Presigner presigner;

    @Autowired
    MediaStorageProperties properties;

    @Test
    @DisplayName("署名付き URL で受付のバケットへ PUT でき、大きさと先頭のバイトを読める")
    void uploadsWithPresignedUrl() throws Exception {
        var key = uploadKey();
        var upload = storage.presignUpload(key, "image/png", PNG.length);

        assertThat(put(upload, PNG)).isEqualTo(200);
        assertThat(upload.headers()).containsEntry("Content-Type", "image/png");
        assertThat(upload.expiresAt()).isAfter(java.time.Instant.now());
        assertThat(storage.findUploadSize(key)).contains((long) PNG.length);
        assertThat(storage.readUploadHead(key, 4)).containsExactly((byte) 0x89, 'P', 'N', 'G');
        assertThat(storage.readUploadHead(key, 100)).containsExactly(PNG);
    }

    @Test
    @DisplayName("受付のないキーは、大きさが空になる")
    void returnsEmptyForMissingUpload() {
        assertThat(storage.findUploadSize(uploadKey())).isEmpty();
    }

    @Test
    @DisplayName("署名を書き換えた URL、別のキーの URL では PUT できない")
    void rejectsTamperedUrl() throws Exception {
        var key = uploadKey();
        var upload = storage.presignUpload(key, "image/png", PNG.length);
        var url = upload.url().toString();

        var tampered = url.replaceFirst("X-Amz-Signature=[0-9a-f]{4}", "X-Amz-Signature=0000");
        assertThat(put(new ObjectStorage.PresignedUpload(URI.create(tampered), upload.headers(), upload.expiresAt()), PNG))
                .isEqualTo(403);
        var otherKey = url.replace(key, uploadKey());
        assertThat(put(new ObjectStorage.PresignedUpload(URI.create(otherKey), upload.headers(), upload.expiresAt()), PNG))
                .isEqualTo(403);
        assertThat(storage.findUploadSize(key)).isEmpty();
    }

    @Test
    @DisplayName("URL の有効期間は設定のとおりで、期限の切れた URL では PUT できない")
    void rejectsExpiredUrl() throws Exception {
        var shortLived = new S3ObjectStorage(s3, presigner, properties.withUploadUrlTtl(Duration.ofMinutes(1)));
        var upload = shortLived.presignUpload(uploadKey(), "image/png", PNG.length);
        assertThat(upload.url().getQuery()).contains("X-Amz-Expires=60");

        // 期限が切れたかの判定は置き場所が行う。時間を待たないよう、署名の時計を 10 分前にずらして、
        // 作った時点で期限（1 分）の切れた URL を作る。同じ作り方で今の時刻に署名した URL は通る（署名そのものは正しい）
        var now = Clock.systemUTC();
        assertThat(putSignedAt(now, uploadKey())).isEqualTo(200);
        assertThat(putSignedAt(Clock.offset(now, Duration.ofMinutes(-10)), uploadKey())).isEqualTo(403);
    }

    @Test
    @DisplayName("配信のバケットへ書き込むと、だれでも読め、形式と長いキャッシュの指定が付く")
    void putsPublished() throws Exception {
        var publicKey = "assets/%s.webp".formatted(UUID.randomUUID());

        storage.putPublished(publicKey, WEBP, "image/webp");

        var response = get(storage.publicUrl(publicKey));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).containsExactly(WEBP);
        assertThat(response.headers().firstValue("Content-Type")).contains("image/webp");
        assertThat(response.headers().firstValue("Cache-Control")).contains("public, max-age=31536000, immutable");
        assertThat(storage.publicUrl(publicKey).toString()).isEqualTo(properties.publicBaseUrl() + "/" + publicKey);
    }

    @Test
    @DisplayName("配信のファイルを消せる。ないファイルを消しても成功する")
    void deletesPublished() {
        var publicKey = "assets/%s.webp".formatted(UUID.randomUUID());
        storage.putPublished(publicKey, WEBP, "image/webp");

        storage.deletePublished(publicKey);

        assertThat(existsPublished(publicKey)).isFalse();
        assertThatCode(() -> storage.deletePublished(publicKey)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("配信のバケットに書けなければ（書く権限がない）、例外になる")
    void failsWhenPublicBucketIsNotWritable() {
        // imgproxy の資格情報は、受付のバケットを読む権限だけを持つ（seaweedfs/s3.json）
        try (var readOnly = S3Client.builder()
                .endpointOverride(properties.endpoint())
                .region(Region.of(properties.region()))
                .forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("integration-test-imgproxy", "integration-test-imgproxy-secret")))
                .build()) {
            var broken = new S3ObjectStorage(readOnly, presigner, properties);

            assertThatThrownBy(() -> broken.putPublished("assets/%s.webp".formatted(UUID.randomUUID()), WEBP, "image/webp"))
                    .isInstanceOfSatisfying(S3Exception.class, ex -> assertThat(ex.statusCode()).isEqualTo(403));
        }
    }

    // ============================== 補助 ==============================

    /**
     * 受付のバケットへの PUT の署名付き URL（有効期間 1 分）を、指定の時計の時刻で署名して作り、PUT する。<br />
     * アプリの署名者は署名の時刻を変えられないので、同じ方式（SigV4、クエリに署名）の署名者を直接使う。
     *
     * @param clock     署名の時刻を取る時計
     * @param uploadKey 受付のキー
     * @return HTTP ステータス
     */
    private int putSignedAt(Clock clock, String uploadKey) throws IOException, InterruptedException {
        var unsigned = SdkHttpRequest.builder()
                .method(SdkHttpMethod.PUT)
                .uri(URI.create(properties.endpoint() + "/" + properties.incomingBucket() + "/" + uploadKey))
                .putHeader("Content-Type", "image/png")
                .build();
        var signed = AwsV4HttpSigner.create().sign(request -> request
                .identity(AwsCredentialsIdentity.create(properties.accessKeyId(), properties.secretAccessKey()))
                .request(unsigned)
                .putProperty(AwsV4HttpSigner.SERVICE_SIGNING_NAME, "s3")
                .putProperty(AwsV4HttpSigner.REGION_NAME, properties.region())
                .putProperty(AwsV4HttpSigner.AUTH_LOCATION, AwsV4HttpSigner.AuthLocation.QUERY_STRING)
                .putProperty(AwsV4HttpSigner.EXPIRATION_DURATION, Duration.ofMinutes(1))
                .putProperty(AwsV4HttpSigner.PAYLOAD_SIGNING_ENABLED, false)
                .putProperty(HttpSigner.SIGNING_CLOCK, clock));
        var request = HttpRequest.newBuilder(signed.request().getUri())
                .header("Content-Type", "image/png")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(PNG));
        return http.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    /**
     * テストごとに別の、受付のバケットのキー（{範囲}/{受付 ID} の形）
     */
    private static String uploadKey() {
        return UUID.randomUUID() + "/" + UUID.randomUUID();
    }

    /**
     * 署名付き URL へ、指定のヘッダーを付けてファイルを PUT し、HTTP ステータスを返す
     */
    private int put(ObjectStorage.PresignedUpload upload, byte[] body) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(upload.url()).PUT(HttpRequest.BodyPublishers.ofByteArray(body));
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
     * 配信のバケットに、そのキーのファイルがあるかを返す
     */
    private boolean existsPublished(String publicKey) {
        return !s3.listObjectsV2(request -> request.bucket(properties.publicBucket()).prefix(publicKey)).contents().isEmpty();
    }
}

package com.ecsite;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

/**
 * 統合テスト用の Testcontainers 設定。<br />
 * PostgreSQL を使い捨てコンテナで起動し、{@code @ServiceConnection} で Spring Boot の接続設定を差し替える。
 * 画像の置き場所（S3 互換）は SeaweedFS で起動し、受付・配信のバケットを作って接続設定を与える（plan 001 第 7 節）。
 * コンテナはテスト JVM 内で一度だけ起動され、全テストクラスで共有される。
 * スキーマは Flyway のマイグレーションで作る（profile {@code integration-test}）。
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    /**
     * 受付のバケット
     */
    public static final String INCOMING_BUCKET = "images-incoming";

    /**
     * 配信のバケット
     */
    public static final String PUBLIC_BUCKET = "images";

    private static final String ACCESS_KEY_ID = "integration-test";

    private static final String SECRET_ACCESS_KEY = "integration-test-secret";

    private static final int S3_PORT = 8333;

    /**
     * PostgreSQL コンテナ。compose.yaml と同じイメージを使う。
     *
     * @return PostgreSQL コンテナ
     */
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
    }

    /**
     * SeaweedFS コンテナ。compose.yaml と同じイメージを使い、資格情報を検証させる（署名付き URL の署名・期限を確かめるため）。<br />
     * 起動したら、受付・配信のバケットを作り、配信のバケットをだれでも読めるようにする。
     *
     * @return SeaweedFS コンテナ
     */
    @Bean
    GenericContainer<?> seaweedfs() {
        var container = new GenericContainer<>(DockerImageName.parse("chrislusf/seaweedfs:4.48"))
                .withCommand("server", "-dir=/data", "-s3", "-s3.port=" + S3_PORT, "-s3.config=/etc/seaweedfs/s3.json")
                .withCopyFileToContainer(MountableFile.forClasspathResource("seaweedfs/s3.json"), "/etc/seaweedfs/s3.json")
                .withExposedPorts(S3_PORT)
                // 認証のない要求は 403 になるが、応答があれば S3 の口の準備ができている
                .waitingFor(Wait.forHttp("/").forPort(S3_PORT).forStatusCode(403));
        container.start();
        createBuckets(endpoint(container));
        return container;
    }

    /**
     * 画像の置き場所の接続設定（application-local.yml と同じ項目）を、SeaweedFS コンテナに向ける。
     *
     * @param seaweedfs SeaweedFS コンテナ
     * @return 接続設定
     */
    @Bean
    DynamicPropertyRegistrar imageStorageProperties(GenericContainer<?> seaweedfs) {
        return registry -> {
            var endpoint = endpoint(seaweedfs);
            registry.add("ecsite.image.storage.endpoint", endpoint::toString);
            registry.add("ecsite.image.storage.region", () -> "us-east-1");
            registry.add("ecsite.image.storage.path-style-access", () -> "true");
            registry.add("ecsite.image.storage.access-key-id", () -> ACCESS_KEY_ID);
            registry.add("ecsite.image.storage.secret-access-key", () -> SECRET_ACCESS_KEY);
            registry.add("ecsite.image.storage.incoming-bucket", () -> INCOMING_BUCKET);
            registry.add("ecsite.image.storage.public-bucket", () -> PUBLIC_BUCKET);
            registry.add("ecsite.image.storage.public-base-url", () -> endpoint + "/" + PUBLIC_BUCKET);
        };
    }

    private static URI endpoint(GenericContainer<?> container) {
        return URI.create("http://" + container.getHost() + ":" + container.getMappedPort(S3_PORT));
    }

    /**
     * 受付・配信のバケットを作り、配信のバケットをだれでも読めるようにする（compose/seaweedfs/init.sh と同じ）。
     */
    private static void createBuckets(URI endpoint) {
        try (var s3 = S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.US_EAST_1)
                .forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(ACCESS_KEY_ID, SECRET_ACCESS_KEY)))
                .build()) {
            s3.createBucket(request -> request.bucket(INCOMING_BUCKET));
            s3.createBucket(request -> request.bucket(PUBLIC_BUCKET));
            s3.putBucketPolicy(request -> request.bucket(PUBLIC_BUCKET).policy("""
                    {"Version": "2012-10-17", "Statement": [{"Effect": "Allow", "Principal": "*",
                     "Action": ["s3:GetObject"], "Resource": ["arn:aws:s3:::%s/*"]}]}""".formatted(PUBLIC_BUCKET)));
        }
    }
}

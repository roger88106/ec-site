package com.ecsite;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.util.Map;

/**
 * 統合テスト用の Testcontainers 設定。<br />
 * PostgreSQL を使い捨てコンテナで起動し、{@code @ServiceConnection} で Spring Boot の接続設定を差し替える。
 * 画像の置き場所（S3 互換）は SeaweedFS で起動し、受付・配信のバケットを作って接続設定を与える（plan 001 第 7 節）。
 * 画像の変換は imgproxy で起動し、同じネットワークから SeaweedFS を読ませる（plan 001 第 7.5 節）。
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

    /**
     * SeaweedFS の資格情報（テスト専用。resources/seaweedfs/s3.json）
     */
    private static final String ACCESS_KEY_ID = "integration-test";

    /**
     * SeaweedFS の資格情報のシークレット（テスト専用）
     */
    private static final String SECRET_ACCESS_KEY = "integration-test-secret";

    private static final int S3_PORT = 8333;

    /**
     * SeaweedFS のボリュームの大きさ（MB）。compose.yaml と同じ
     */
    private static final int VOLUME_SIZE_LIMIT_MB = 100;

    /**
     * コンテナ同士のネットワークの中での SeaweedFS の名前
     */
    private static final String SEAWEEDFS_ALIAS = "seaweedfs";

    private static final int IMGPROXY_PORT = 8080;

    /**
     * imgproxy の URL の署名の鍵（16 進。テスト専用）
     */
    private static final String IMGPROXY_KEY = "696e746567726174696f6e2d746573742d6b6579";

    /**
     * imgproxy の URL の署名のソルト（16 進。テスト専用）
     */
    private static final String IMGPROXY_SALT = "696e746567726174696f6e2d746573742d73616c74";

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
     * @param network コンテナ同士のネットワーク
     * @return SeaweedFS コンテナ
     */
    @Bean
    GenericContainer<?> seaweedfs(Network network) {
        var container = new GenericContainer<>(DockerImageName.parse("chrislusf/seaweedfs:4.48"))
                // ボリュームの数の上限は「空きの容量 ÷ ボリュームの大きさ（既定 30GB）」になる。空きの少ない CI の
                // 環境では 2 つ目のバケットにボリュームを割り当てられず 500 になるので、ボリュームを小さくする
                .withCommand("server", "-dir=/data", "-s3", "-s3.port=" + S3_PORT, "-s3.config=/etc/seaweedfs/s3.json",
                        "-master.volumeSizeLimitMB=" + VOLUME_SIZE_LIMIT_MB)
                .withCopyFileToContainer(MountableFile.forClasspathResource("seaweedfs/s3.json"), "/etc/seaweedfs/s3.json")
                .withNetwork(network)
                .withNetworkAliases(SEAWEEDFS_ALIAS)
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
            registry.add("ecsite.media.storage.endpoint", endpoint::toString);
            registry.add("ecsite.media.storage.region", () -> "us-east-1");
            registry.add("ecsite.media.storage.path-style-access", () -> "true");
            registry.add("ecsite.media.storage.access-key-id", () -> ACCESS_KEY_ID);
            registry.add("ecsite.media.storage.secret-access-key", () -> SECRET_ACCESS_KEY);
            registry.add("ecsite.media.storage.incoming-bucket", () -> INCOMING_BUCKET);
            registry.add("ecsite.media.storage.public-bucket", () -> PUBLIC_BUCKET);
            registry.add("ecsite.media.storage.public-base-url", () -> endpoint + "/" + PUBLIC_BUCKET);
        };
    }

    /**
     * コンテナ同士のネットワーク（imgproxy が SeaweedFS を名前で読む）
     *
     * @return ネットワーク
     */
    @Bean(destroyMethod = "close")
    Network network() {
        return Network.newNetwork();
    }

    /**
     * imgproxy コンテナ（画像の変換。plan 001 第 7.5 節）。compose.yaml と同じイメージと設定を使う。<br />
     * 受付のバケットを、読むだけの資格情報で、SeaweedFS のネットワークの中の名前で読む。
     *
     * @param network   コンテナ同士のネットワーク
     * @param seaweedfs SeaweedFS コンテナ（先に起動しておく）
     * @return imgproxy コンテナ
     */
    @Bean
    GenericContainer<?> imgproxy(Network network, GenericContainer<?> seaweedfs) {
        var container = new GenericContainer<>(DockerImageName.parse("darthsim/imgproxy:v4.0.17"))
                .withNetwork(network)
                .withEnv(Map.ofEntries(
                        Map.entry("IMGPROXY_KEY", IMGPROXY_KEY),
                        Map.entry("IMGPROXY_SALT", IMGPROXY_SALT),
                        Map.entry("IMGPROXY_USE_S3", "true"),
                        Map.entry("IMGPROXY_S3_ENDPOINT", "http://" + SEAWEEDFS_ALIAS + ":" + S3_PORT),
                        Map.entry("IMGPROXY_S3_ENDPOINT_USE_PATH_STYLE", "true"),
                        Map.entry("IMGPROXY_S3_REGION", "us-east-1"),
                        Map.entry("AWS_ACCESS_KEY_ID", "integration-test-imgproxy"),
                        Map.entry("AWS_SECRET_ACCESS_KEY", "integration-test-imgproxy-secret"),
                        Map.entry("IMGPROXY_ALLOWED_SOURCES", "s3://" + INCOMING_BUCKET + "/"),
                        Map.entry("IMGPROXY_MAX_SRC_FILE_SIZE", "10485760"),
                        Map.entry("IMGPROXY_MAX_SRC_RESOLUTION", "60"),
                        Map.entry("IMGPROXY_TIMEOUT", "30"),
                        Map.entry("IMGPROXY_WORKERS", "2"),
                        Map.entry("IMGPROXY_AUTO_ROTATE", "true"),
                        Map.entry("IMGPROXY_STRIP_METADATA", "true")))
                .withExposedPorts(IMGPROXY_PORT)
                .waitingFor(Wait.forHttp("/health").forPort(IMGPROXY_PORT));
        container.start();
        return container;
    }

    /**
     * 画像の変換の接続設定（application-local.yml と同じ項目）を、imgproxy コンテナに向ける。
     *
     * @param imgproxy imgproxy コンテナ
     * @return 接続設定
     */
    @Bean
    DynamicPropertyRegistrar imageConverterProperties(GenericContainer<?> imgproxy) {
        return registry -> {
            registry.add("ecsite.media.converter.base-url",
                    () -> "http://" + imgproxy.getHost() + ":" + imgproxy.getMappedPort(IMGPROXY_PORT));
            registry.add("ecsite.media.converter.key", () -> IMGPROXY_KEY);
            registry.add("ecsite.media.converter.salt", () -> IMGPROXY_SALT);
        };
    }

    /**
     * テストの JVM から見た、SeaweedFS の S3 の口の URL（割り当てられたポート）
     */
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

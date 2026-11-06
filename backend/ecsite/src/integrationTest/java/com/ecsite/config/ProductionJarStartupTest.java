package com.ecsite.config;

import com.ecsite.AsyncEvents;
import com.ecsite.TestcontainersConfig;
import org.awaitility.core.ConditionTimeoutException;
import org.awaitility.core.TerminalFailureException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 本番の jar の起動の統合テスト（バックエンドのアーキテクチャの「公開ネットワークへのデプロイの前提」）。<br />
 * 本番の jar には、ローカルの設定ファイルが入らない。ローカルの設定から物流の mock フラグだけを除いて渡し、
 * 本番の jar を別のプロセスで起動して、起動に成功し、物流の mock の画面が見つからないことを確認する。
 * jar の中にローカルの設定が入っていてフラグが読まれれば、画面のエンドポイントができて 404 でなくなり、このテストが失敗する。
 * Spring のコンテキストを 1 つのテストの中で作るテストでは、Bean を作る順と設定の読み方が本番の jar と違い、確認できないため。
 */
class ProductionJarStartupTest {

    /**
     * 起動を待つ時間の上限。Spring の起動を別のプロセスで待つので、非同期のリスナーのタイムアウトより長く待つ
     */
    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(2);

    /**
     * 物流の mock フラグの名前
     */
    private static final String SHIPPING_MOCK_FLAG = "ecsite.shipping.mock.enabled";

    /**
     * リダイレクトを追わない HTTP クライアント
     */
    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    /**
     * ローカルの設定とログを置くディレクトリ
     */
    @TempDir
    static Path dir;

    /**
     * 本番の jar が接続する PostgreSQL
     */
    private static PostgreSQLContainer postgres;

    /**
     * 本番の jar のプロセス
     */
    private static Process process;

    /**
     * 本番の jar の業務ポート
     */
    private static int businessPort;

    /**
     * 本番の jar を、物流の mock フラグを除いたローカルの設定で起動し、運用ポートの準備の完了を待つ。
     */
    @BeforeAll
    static void startProductionJar() throws Exception {
        postgres = new PostgreSQLContainer(DockerImageName.parse(TestcontainersConfig.POSTGRES_IMAGE));
        postgres.start();
        Files.writeString(dir.resolve("application-local.yml"), withoutShippingMock(localConfig()));
        businessPort = freePort();
        var managementPort = freePort();

        var command = new ArrayList<>(List.of(javaCommand(), "-jar", System.getProperty("ecsite.boot-jar"),
                "--spring.profiles.active=local",
                "--spring.config.additional-location=" + dir.toAbsolutePath().toUri(),
                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword(),
                // 確認する URL は DB を使わないので、マイグレーションと seed は適用しない（起動の時間を短くするため）
                "--spring.flyway.enabled=false",
                "--server.port=" + businessPort,
                "--management.server.port=" + managementPort));
        process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(logFile().toFile()).start();

        try {
            await().atMost(STARTUP_TIMEOUT)
                    .failFast("本番の jar が起動の途中で止まった", () -> !process.isAlive())
                    .until(() -> isReady(managementPort));
        } catch (ConditionTimeoutException | TerminalFailureException e) {
            throw new AssertionError("本番の jar が起動しない。ログ：\n" + readLog(), e);
        }
    }

    /**
     * 本番の jar のプロセスと PostgreSQL を停止する。
     */
    @AfterAll
    static void stopProductionJar() throws InterruptedException {
        if (process != null) {
            assertThat(process.destroyForcibly().waitFor(STARTUP_TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                    .as("本番の jar のプロセスが停止する").isTrue();
        }
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    @DisplayName("AC-245 本番の jar を、物流のフラグを除いたローカルの設定で起動すると、mock の画面の GET と通知の操作の POST は 404 になる")
    void mockCarrierPageIsNotFound() throws Exception {
        assertThat(send(HttpRequest.newBuilder(businessUri("/mock-carrier")).GET()).statusCode())
                .as("画面の GET").isEqualTo(404);

        var notify = HttpRequest.newBuilder(businessUri("/mock-carrier/orders/" + UUID.randomUUID() + "/notify"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("status=RETURNED"));
        assertThat(send(notify).statusCode()).as("通知の操作の POST").isEqualTo(404);
    }

    @Test
    @DisplayName("AC-410-2 物流のフラグを立てずに起動すると、物流サービスに接続していないことを、フラグの名前と一緒に警告のログに 1 回出力する")
    void warnsOnceAboutUnconnectedCarrier() throws IOException {
        assertThat(readLog().lines())
                .filteredOn(line -> line.contains("WARN") && line.contains(SHIPPING_MOCK_FLAG))
                .singleElement()
                .asString()
                .contains("物流サービスに接続していない");
    }

    @Test
    @DisplayName("物流のフラグを立てないときも、通知のエンドポイントは残り、署名のない POST を 401 で拒否する")
    void carrierWebhookRejectsUnsignedPost() throws Exception {
        var response = send(HttpRequest.newBuilder(businessUri("/api/webhooks/carrier"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}")));

        assertThat(response.statusCode()).isEqualTo(401);
        // 認可で拒否された 401 は本文（Problem Details）を持つ。本文が空なら、エンドポイントの署名の確認で拒否された
        assertThat(response.body()).isEmpty();
    }

    /**
     * 運用ポートの準備の完了を確認する。接続できない間は、まだ準備ができていないとみなす。
     *
     * @param managementPort 運用ポート
     * @return 準備ができていれば true
     */
    private static boolean isReady(int managementPort) throws InterruptedException {
        var uri = URI.create("http://localhost:" + managementPort + "/actuator/health/readiness");
        try {
            return send(HttpRequest.newBuilder(uri).GET()).statusCode() == 200;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * リクエストを送る。
     *
     * @param request リクエスト（タイムアウトを付けて送る）
     * @return 応答
     */
    private static HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return HTTP.send(request.timeout(AsyncEvents.TIMEOUT).build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 業務ポートの URL を返す。
     *
     * @param path パス
     * @return URL
     */
    private static URI businessUri(String path) {
        return URI.create("http://localhost:" + businessPort + path);
    }

    /**
     * 空いているポートを返す。
     */
    private static int freePort() throws IOException {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /**
     * 本番の jar の出力を書くファイルを返す。
     */
    private static Path logFile() {
        return dir.resolve("startup.log");
    }

    /**
     * 本番の jar の出力を読む。
     */
    private static String readLog() throws IOException {
        return Files.readString(logFile(), StandardCharsets.UTF_8);
    }

    /**
     * テストを実行している Java のコマンドを返す（本番の jar も同じバージョンの Java で起動する）。
     */
    private static String javaCommand() {
        return ProcessHandle.current().info().command().orElse("java");
    }

    /**
     * ローカルの設定ファイル（main のリソース）を読む。
     */
    private static String localConfig() throws IOException {
        try (InputStream in = ProductionJarStartupTest.class.getResourceAsStream("/application-local.yml")) {
            assertThat(in).as("ローカルの設定ファイル").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * ローカルの設定から、物流の mock フラグ（ecsite.shipping.mock）の項目だけを除く。
     *
     * @param yaml ローカルの設定
     * @return 物流の mock の項目を除いた設定
     */
    static String withoutShippingMock(String yaml) {
        var kept = new ArrayList<String>();
        var inShipping = false;
        var inMock = false;
        for (String line : yaml.split("\n", -1)) {
            var indent = line.length() - line.stripLeading().length();
            var blank = line.isBlank();
            if (!blank && indent <= 2) {
                inShipping = line.stripTrailing().equals("  shipping:");
                inMock = false;
            }
            if (inShipping && !blank && indent == 4) {
                inMock = line.strip().equals("mock:");
            }
            if (inMock && (blank || indent >= 4)) {
                continue;
            }
            kept.add(line);
        }
        var result = String.join("\n", kept);
        assertThat(mockBlocks(result)).as("物流の mock の項目だけを除く").isEqualTo(mockBlocks(yaml) - 1);
        assertThat(yaml.lines().count() - result.lines().count()).as("除く行（mock:、説明、enabled）").isLessThanOrEqualTo(3);
        assertThat(result).as("物流のほかの設定と、後ろの設定は残す").contains("webhook-secret", "issuer-uri");
        return result;
    }

    /**
     * 設定の中の、mock の項目の数を返す。
     */
    private static long mockBlocks(String yaml) {
        return yaml.lines().filter(line -> line.strip().equals("mock:")).count();
    }
}

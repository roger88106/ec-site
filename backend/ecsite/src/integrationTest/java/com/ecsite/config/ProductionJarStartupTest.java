package com.ecsite.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 本番の jar の起動の失敗の統合テスト（バックエンドのアーキテクチャの「公開ネットワークへのデプロイの前提」）。<br />
 * 本番の jar には、ローカルの設定ファイルが入らない。ローカルの設定から物流の mock フラグだけを除いて渡し、
 * 本番の jar を別のプロセスで起動して、フラグの名前を出して止まることを確認する。
 * Spring のコンテキストを 1 つのテストの中で作るテストでは、Bean を作る順が本番の jar と違い、確認できないため。
 */
class ProductionJarStartupTest {

    /**
     * 起動を待つ時間の上限（秒）
     */
    private static final long TIMEOUT_SECONDS = 120;

    /**
     * 物流の mock フラグの名前
     */
    private static final String SHIPPING_MOCK_FLAG = "ecsite.shipping.mock.enabled";

    @Test
    @DisplayName("AC-245 本番の jar を、物流の mock フラグのないローカルの設定で起動すると、フラグの名前を出して起動に失敗する")
    void productionJarStopsWithoutShippingFlag(@TempDir Path dir) throws Exception {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))) {
            postgres.start();
            Files.writeString(dir.resolve("application-local.yml"), withoutShippingMock(localConfig()));
            var log = dir.resolve("startup.log");

            var command = new ArrayList<>(List.of(javaCommand(), "-jar", System.getProperty("ecsite.boot-jar"),
                    "--spring.profiles.active=local",
                    "--spring.config.additional-location=" + dir.toAbsolutePath().toUri(),
                    "--spring.datasource.url=" + postgres.getJdbcUrl(),
                    "--spring.datasource.username=" + postgres.getUsername(),
                    "--spring.datasource.password=" + postgres.getPassword(),
                    // 確認したいのは設定の不足による失敗なので、マイグレーションと seed は適用しない（起動の時間を短くするため）
                    "--spring.flyway.enabled=false",
                    "--server.port=0",
                    "--management.server.port=0"));
            var process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            var finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
            }

            assertThat(finished).as("本番の jar が時間内に止まる").isTrue();
            assertThat(process.exitValue()).as("終了コード").isNotZero();
            assertThat(Files.readString(log, StandardCharsets.UTF_8)).contains(SHIPPING_MOCK_FLAG);
        }
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

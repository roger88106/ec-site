package com.ecsite.config;

import com.ecsite.TestSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 運用ポートの設定の検査。<br />
 * 起動のときと同じ順（共通の設定、指定した profile の順）に設定ファイルを重ね、後のものが先のものを上書きするとして読む。
 */
class ManagementPortConfigTest {

    /**
     * 運用ポート
     */
    private static final String MANAGEMENT_PORT = "management.server.port";

    /**
     * 業務ポート
     */
    private static final String SERVER_PORT = "server.port";

    /**
     * Spring Boot の業務ポートの既定
     */
    private static final String DEFAULT_SERVER_PORT = "8080";

    @Test
    @DisplayName("AC-237 運用ポートの既定は 9080 で、環境変数で変えられる")
    void defaultManagementPort() {
        assertThat(value(MANAGEMENT_PORT)).hasValue("${MANAGEMENT_SERVER_PORT:9080}");
    }

    @Test
    @DisplayName("AC-237 ローカルの運用ポートは、業務ポートとぶつからない")
    void localPortsDiffer() {
        assertThat(resolved(MANAGEMENT_PORT, "local")).isEqualTo("9080");
        assertThat(value(SERVER_PORT, "local").orElse(DEFAULT_SERVER_PORT)).isEqualTo(DEFAULT_SERVER_PORT);
    }

    @Test
    @DisplayName("AC-237 E2E の運用ポートは 9090 で、E2E の業務ポートとぶつからない")
    void e2ePortsDiffer() {
        assertThat(resolved(MANAGEMENT_PORT, "local", "e2e")).isEqualTo("9090");
        assertThat(value(SERVER_PORT, "local", "e2e")).hasValue("8090");
    }

    /**
     * 設定ファイルを重ねたときの値の、環境変数がないときの値（{@code ${名前:既定}} の既定）。
     */
    private static String resolved(String name, String... profiles) {
        var raw = value(name, profiles).orElseThrow();
        var placeholder = raw.matches("\\$\\{[^:}]+:[^}]*}");
        return placeholder ? raw.substring(raw.indexOf(':') + 1, raw.length() - 1) : raw;
    }

    /**
     * 設定ファイルを重ねたときの値。どれにも書かれていなければ空。
     */
    private static Optional<String> value(String name, String... profiles) {
        return Optional.ofNullable(TestSettings.load(profiles).get(name));
    }
}

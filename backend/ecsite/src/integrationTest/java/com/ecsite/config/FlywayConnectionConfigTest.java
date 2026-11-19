package com.ecsite.config;

import com.ecsite.support.config.TestcontainersConfig;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * マイグレーション（Flyway）の接続の設定の検証
 *
 * <p>DB の接続の URL・ユーザー・パスワードを設定の値で与える形（本番と local の形）で、Flyway の接続に、アプリの接続（プール）の
 * タイムアウトが付かないことを確認する。Testcontainers の接続の差し替え（{@code @ServiceConnection}）は、Flyway の接続先も差し替えるので、
 * それを使う統合テストでは確認できない。
 */
class FlywayConnectionConfigTest {

    // ============================== 定数 ==============================

    /**
     * 確認する設定
     *
     * <p>PostgreSQL のサーバーの設定の名前。ロック待ち、文の実行、トランザクション中のアイドルのタイムアウト。
     */
    private static final List<String> SETTINGS = List.of("lock_timeout", "statement_timeout", "idle_in_transaction_session_timeout");

    // ============================== 状態 ==============================

    /**
     * PostgreSQL コンテナ
     */
    private static PostgreSQLContainer postgres;

    @BeforeAll
    static void startPostgres() {
        postgres = new PostgreSQLContainer(DockerImageName.parse(TestcontainersConfig.POSTGRES_IMAGE));
        postgres.start();
    }

    @AfterAll
    static void stopPostgres() {
        postgres.stop();
    }

    // ============================== テスト ==============================

    @Test
    @DisplayName("DC-556-2 マイグレーションの接続には、アプリの接続の 3 つのタイムアウトが付かない")
    void flywayConnectionHasNoTimeouts() throws SQLException {
        // 準備：設定の値で接続先を与え、アプリの接続にタイムアウトを置く（application.yml と同じ形）
        runner().run(context -> {
            // 確認の前提：アプリの接続（プール）には、タイムアウトが付く
            assertThat(settingsOf(context.getBean(DataSource.class)))
                    .containsExactly("5s", "30s", "1min");

            // 操作と確認：Flyway が使う接続には、どれも付かない
            assertThat(settingsOf(context.getBean(Flyway.class).getConfiguration().getDataSource()))
                    .containsExactly("0", "0", "0");
        });
    }

    // ============================== 確認の補助 ==============================

    /**
     * 接続先を設定の値で与える、Flyway の接続とプールだけの Spring のコンテキスト。
     *
     * @return コンテキストを起動する補助
     */
    private static ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class))
                .withUserConfiguration(FlywayConnectionConfig.class)
                .withPropertyValues(
                        "spring.datasource.url=" + postgres.getJdbcUrl(),
                        "spring.datasource.username=" + postgres.getUsername(),
                        "spring.datasource.password=" + postgres.getPassword(),
                        "spring.datasource.hikari.data-source-properties.options="
                                + "-c lock_timeout=5s -c statement_timeout=30s -c idle_in_transaction_session_timeout=60s",
                        "spring.flyway.enabled=true",
                        // マイグレーションは使わない（接続だけを見る）
                        "spring.flyway.locations=classpath:no-migration-here");
    }

    /**
     * 接続で、3 つのタイムアウトの設定を読む。
     *
     * @param dataSource 接続を取る DataSource
     * @return {@link #SETTINGS} の順の値
     */
    private static List<String> settingsOf(DataSource dataSource) throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            var values = new ArrayList<String>();
            for (var setting : SETTINGS) {
                try (var result = statement.executeQuery("SHOW " + setting)) {
                    result.next();
                    values.add(result.getString(1));
                }
            }
            return values;
        }
    }
}

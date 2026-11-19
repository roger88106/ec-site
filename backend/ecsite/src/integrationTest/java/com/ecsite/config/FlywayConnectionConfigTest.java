package com.ecsite.config;

import com.ecsite.support.config.TestcontainersConfig;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayConnectionDetails;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * マイグレーション（Flyway）の接続の設定の検証
 *
 * <p>DB の接続の URL・ユーザー・パスワードを設定の値で与える形（本番と local の形）で、Flyway の接続に、アプリの接続（プール）の
 * タイムアウトが付かないこと、PostgreSQL のエラーの詳細を例外のメッセージに入れない設定が付くこと、{@code spring.flyway.url} を優先することを確認する。
 * Testcontainers の接続の差し替え（{@code @ServiceConnection}）は、Flyway の接続先も差し替えるので、
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
    void flywayConnectionHasNoTimeouts() {
        // 準備：設定の値で接続先を与え、アプリの接続にタイムアウトを置く（既定の 5 秒、30 秒、60 秒）
        runner().run(context -> {
            // 確認の前提：アプリの接続（プール）には、タイムアウトが付く
            assertThat(settingsOf(context.getBean(DataSource.class)))
                    .containsExactly("5s", "30s", "1min");

            // 操作と確認：Flyway が使う接続には、どれも付かない
            assertThat(settingsOf(context.getBean(Flyway.class).getConfiguration().getDataSource()))
                    .containsExactly("0", "0", "0");
        });
    }

    @Test
    @DisplayName("DC-556-1 設定した 3 つのタイムアウトが、アプリの接続に付く")
    void appConnectionHasConfiguredTimeouts() {
        // 準備：3 つのタイムアウトを設定で変える
        runner("ecsite.db.lock-timeout=500ms", "ecsite.db.statement-timeout=2m", "ecsite.db.idle-in-transaction-timeout=90s")
                .run(context ->
                        // 操作と確認：アプリの接続に、設定した値が付く
                        assertThat(settingsOf(context.getBean(DataSource.class)))
                                .containsExactly("500ms", "2min", "90s"));
    }

    @Test
    @DisplayName("マイグレーションの接続は、PostgreSQL のエラーの詳細（行の値）を例外のメッセージに入れない")
    void flywayConnectionHidesServerErrorDetail() {
        // 準備：一意制約のある表
        runner().run(context -> {
            var flywayDataSource = context.getBean(Flyway.class).getConfiguration().getDataSource();
            try (var connection = flywayDataSource.getConnection(); var statement = connection.createStatement()) {
                statement.execute("CREATE TEMP TABLE detail_check (code text PRIMARY KEY)");
                statement.execute("INSERT INTO detail_check VALUES ('secret-row-value')");

                // 操作と確認：一意制約の違反の例外のメッセージに、行の値（Key (列)=(値)）が入らない
                assertThatThrownBy(() -> statement.execute("INSERT INTO detail_check VALUES ('secret-row-value')"))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("detail_check_pkey")
                        .hasMessageNotContaining("secret-row-value");
            }
        });
    }

    @ParameterizedTest(name = "「{0}」→「{1}」")
    @CsvSource({
            "jdbc:postgresql://localhost:5432/db, jdbc:postgresql://localhost:5432/db?logServerErrorDetail=false",
            "jdbc:postgresql://localhost:5432/db?loggerLevel=OFF, jdbc:postgresql://localhost:5432/db?loggerLevel=OFF&logServerErrorDetail=false"
    })
    @DisplayName("URL に引数がなければ ? で、あれば & で、エラーの詳細を入れない引数をつなぐ")
    void appendsServerErrorDetailParameter(String url, String expected) {
        // 操作と確認：引数の有無で、つなぎの記号が変わる
        assertThat(FlywayConnectionConfig.withoutServerErrorDetail(url)).isEqualTo(expected);
    }

    @Test
    @DisplayName("spring.flyway.url が設定されたら、その接続先を使う（アプリの接続が使う接続先で上書きしない）")
    void flywayUrlSettingTakesPrecedence() {
        // 準備：アプリの接続先は存在しない DB にし、Flyway の接続先だけ本物の DB にする
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class))
                .withUserConfiguration(FlywayConnectionConfig.class, EnableDbTimeoutProperties.class)
                .withPropertyValues(
                        "spring.datasource.url=jdbc:postgresql://localhost:1/none",
                        "spring.datasource.username=none",
                        "spring.datasource.password=none",
                        "spring.flyway.enabled=true",
                        "spring.flyway.url=" + postgres.getJdbcUrl(),
                        "spring.flyway.user=" + postgres.getUsername(),
                        "spring.flyway.password=" + postgres.getPassword(),
                        "spring.flyway.locations=classpath:no-migration-here")
                .run(context -> {
                    // 確認：Flyway の接続先は設定の値で、Flyway の接続は使える
                    assertThat(context.getBean(FlywayConnectionDetails.class).getJdbcUrl()).isEqualTo(postgres.getJdbcUrl());
                    assertThat(settingsOf(context.getBean(Flyway.class).getConfiguration().getDataSource()))
                            .containsExactly("0", "0", "0");
                });
    }

    // ============================== 確認の補助 ==============================

    /**
     * 接続先を設定の値で与える、Flyway の接続とプールだけの Spring のコンテキスト。
     *
     * @param extraProperties 加える設定の値
     * @return コンテキストを起動する補助
     */
    private static ApplicationContextRunner runner(String... extraProperties) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class))
                .withUserConfiguration(FlywayConnectionConfig.class, DbTimeoutConfig.class, EnableDbTimeoutProperties.class)
                .withPropertyValues(
                        "spring.datasource.url=" + postgres.getJdbcUrl(),
                        "spring.datasource.username=" + postgres.getUsername(),
                        "spring.datasource.password=" + postgres.getPassword(),
                        "spring.flyway.enabled=true",
                        // マイグレーションは使わない（接続だけを見る）
                        "spring.flyway.locations=classpath:no-migration-here")
                .withPropertyValues(extraProperties);
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

    /**
     * DB のタイムアウトの設定の型を登録する設定（本番は {@code @ConfigurationPropertiesScan} が登録する）
     */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DbTimeoutConfig.Properties.class)
    static class EnableDbTimeoutProperties {
    }
}

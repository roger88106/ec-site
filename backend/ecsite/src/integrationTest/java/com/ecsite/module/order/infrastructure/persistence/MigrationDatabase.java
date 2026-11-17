package com.ecsite.module.order.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

/**
 * マイグレーションのテストの補助
 *
 * <p>テストごとに別のデータベースを作り、マイグレーションの前のバージョンまで流す。
 * テストは、移行の前の形のデータを入れてから、{@link #flyway()} で残りを流す。
 * 作ったデータベースは消さない（コンテナと一緒に捨てる）。
 */
final class MigrationDatabase {

    /**
     * テストのデータベース
     */
    private final DriverManagerDataSource dataSource;

    /**
     * テストのデータベースの SQL
     */
    private final JdbcTemplate jdbc;

    /**
     * テストのデータベースにつなぐ。
     *
     * @param dataSource テストのデータベース
     */
    private MigrationDatabase(DriverManagerDataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /**
     * 新しいデータベースを作り、指定のバージョンまで流す。
     *
     * @param postgres 統合テストの PostgreSQL
     * @param target   流す最後のバージョン（マイグレーションの前のバージョン）
     * @return テストのデータベース
     * @throws SQLException データベースを作れない
     */
    static MigrationDatabase migratedTo(PostgreSQLContainer postgres, String target) throws SQLException {
        var name = "migration_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + name);
        }
        var database = new MigrationDatabase(new DriverManagerDataSource("jdbc:postgresql://%s:%d/%s".formatted(postgres.getHost(),
                postgres.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT), name), postgres.getUsername(), postgres.getPassword()));
        database.configuration().target(target).load().migrate();
        return database;
    }

    /**
     * テストのデータベースの SQL を返す。
     *
     * @return テストのデータベースの SQL
     */
    JdbcTemplate jdbc() {
        return jdbc;
    }

    /**
     * 残りを最新まで流す Flyway を返す。
     *
     * @return テストのデータベースの Flyway（{@code migrate} で残りを流す）
     */
    Flyway flyway() {
        return configuration().load();
    }

    /**
     * テストのデータベースに、本番と同じマイグレーションを流す設定を返す。
     *
     * @return Flyway の設定
     */
    private FluentConfiguration configuration() {
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration");
    }
}

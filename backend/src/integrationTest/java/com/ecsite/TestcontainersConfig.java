package com.ecsite;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 統合テスト用の Testcontainers 設定。<br />
 * PostgreSQL を使い捨てコンテナで起動し、{@code @ServiceConnection} で Spring Boot の接続設定を差し替える。
 * コンテナはテスト JVM 内で一度だけ起動され、全テストクラスで共有される。
 * スキーマは Flyway のマイグレーションで作る（profile {@code integration-test}）。
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

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
}

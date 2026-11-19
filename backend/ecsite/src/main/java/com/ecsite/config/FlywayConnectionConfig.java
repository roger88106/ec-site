package com.ecsite.config;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.flyway.autoconfigure.FlywayConnectionDetails;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * マイグレーション（Flyway）の接続の設定
 *
 * <p>Flyway に、アプリのプール（Hikari）とは別の接続を作らせる。接続先は、アプリの接続の接続先（{@link JdbcConnectionDetails}）と同じにする。
 * アプリの接続に置くタイムアウト（ロック待ち、文の実行、トランザクション中のアイドル。{@code application.yml} の {@code ecsite.db}）は、
 * プールの接続の設定なので、この接続には付かない。索引の作成など、長いマイグレーションを止めないため。
 *
 * <p>プールの接続の設定を Flyway の中で戻す方法は使わない。接続がプールに戻ったあとも設定が残り、アプリの処理に影響するため。
 */
@Configuration
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = true)
class FlywayConnectionConfig {

    /**
     * Flyway の接続先を、アプリの接続の接続先と同じにする。
     *
     * <p>接続先の URL を指定すると、Spring Boot は Flyway 用の別の接続（プールを使わない）を作る。
     *
     * @param jdbc アプリの接続の接続先
     * @return Flyway の接続先
     */
    @Bean
    FlywayConnectionDetails flywayConnectionDetails(JdbcConnectionDetails jdbc) {
        return new FlywayConnectionDetails() {
            @Override
            public @Nullable String getUsername() {
                return jdbc.getUsername();
            }

            @Override
            public @Nullable String getPassword() {
                return jdbc.getPassword();
            }

            @Override
            public @Nullable String getJdbcUrl() {
                return jdbc.getJdbcUrl();
            }
        };
    }
}

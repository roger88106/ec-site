package com.ecsite.config;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.flyway.autoconfigure.FlywayConnectionDetails;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * マイグレーション（Flyway）の接続の設定
 *
 * <p>Flyway に、アプリのプール（Hikari）とは別の接続を作らせる。接続先は、アプリの接続が使う接続先（{@link JdbcConnectionDetails}）と同じにする。
 * アプリの接続に置くタイムアウト（ロック待ち、文の実行、トランザクション中のアイドル。{@link DbTimeoutConfig}）は、
 * プールの接続の設定なので、この接続には付かない。索引の作成など、長いマイグレーションを止めないため。
 * 一方、PostgreSQL のエラーの詳細（行の値を含む）を例外のメッセージに入れない設定（{@code logServerErrorDetail=false}）は、
 * 接続の URL の引数なので、この接続にも付ける。マイグレーションの失敗のログに、行の値を残さないため。
 *
 * <p>プールの接続の設定を Flyway の処理の中で元に戻す方法は使わない。接続がプールに戻ったあとも設定が残り、アプリの処理に影響するため。
 */
@Configuration
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = true)
class FlywayConnectionConfig {

    /**
     * PostgreSQL のエラーの詳細を、例外のメッセージに入れない URL の引数
     */
    static final String NO_SERVER_ERROR_DETAIL = "logServerErrorDetail=false";

    /**
     * Flyway の接続先を、アプリの接続が使う接続先と同じにする。
     *
     * <p>接続先の URL を指定すると、Spring Boot は Flyway 用の別の接続（プールを使わない）を作る。
     *
     * <p>{@code spring.flyway.url} が設定されたときは、この Bean を作らず、Spring Boot の既定（{@code spring.flyway.url・user・password}）に任せる。
     * {@link FlywayConnectionDetails} の Bean があると、Spring Boot は設定の値から作る Bean を作らず、設定の値が黙って無視されるため。
     * 本番で、DDL の権限を持つ別のユーザーを渡す形にできるようにする。そのときは、Flyway の接続に {@code logServerErrorDetail=false} は付かないので、
     * 渡す URL に自分で付ける。
     *
     * @param jdbc アプリの接続が使う接続先
     * @return Flyway の接続先
     */
    @Bean
    @ConditionalOnExpression("'${spring.flyway.url:}'.isEmpty()")
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
                return withoutServerErrorDetail(jdbc.getJdbcUrl());
            }
        };
    }

    /**
     * URL に、エラーの詳細を入れない引数を追加する。URL に引数があるときは {@code &} で、ないときは {@code ?} でつなぐ。
     *
     * @param url アプリの接続の URL
     * @return 引数を追加した URL
     */
    static String withoutServerErrorDetail(String url) {
        return url + (url.contains("?") ? "&" : "?") + NO_SERVER_ERROR_DETAIL;
    }
}

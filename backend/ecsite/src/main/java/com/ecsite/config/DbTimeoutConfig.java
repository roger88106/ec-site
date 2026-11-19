package com.ecsite.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * DB の接続のタイムアウトの設定
 *
 * <p>アプリの接続（プール）の初期化で、PostgreSQL のサーバーの設定（接続の {@code options}）としてタイムアウトを置く。
 * 値は設定の型（{@link Properties}）で受けるので、下限を割った値や書き方の誤りは、起動で失敗する。
 *
 * <p>マイグレーション（Flyway）の別の接続には付かない（{@link FlywayConnectionConfig}）。
 */
@Configuration
class DbTimeoutConfig {

    /**
     * プールの接続の {@code options} に、タイムアウトを追加する後処理を作る。
     *
     * <p>プールは最初の接続の取得まで始まらないので、Bean の初期化のときに追加すれば全ての接続に付く。
     * 設定の型は、後処理の作成を早めないよう、使う時点で取る。
     *
     * <p>既に {@code options}（{@code spring.datasource.hikari.data-source-properties.options}）があるときは、
     * それを残し、後ろに空白でつないでタイムアウトを追加する。
     * 接続の URL に {@code options} の引数があると、pgjdbc では URL の値が優先されてタイムアウトが付かないので、URL には {@code options} を書かない。
     *
     * @param properties タイムアウトの設定
     * @return 後処理
     */
    @Bean
    static BeanPostProcessor dbTimeoutPostProcessor(ObjectProvider<Properties> properties) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) {
                if (bean instanceof HikariDataSource dataSource) {
                    var timeoutOptions = properties.getObject().options();
                    // 運用で渡された options（例：-c search_path=...）を消さないよう、後ろにつなぐ
                    var existing = dataSource.getDataSourceProperties().get("options");
                    dataSource.addDataSourceProperty("options",
                            existing == null ? timeoutOptions : existing + " " + timeoutOptions);
                }
                return bean;
            }
        };
    }

    /**
     * DB の接続のタイムアウト
     *
     * <p>環境変数で上書きする（例：ECSITE_DB_LOCK_TIMEOUT）。PostgreSQL は、時間をミリ秒で受け取る。
     *
     * @param lockTimeout              ロック待ち：行ロックと advisory lock を待つ上限。超えると 503（{@code RESOURCE_BUSY}）。
     *                                 消費者・運営者のリクエストが、画面の操作が待てる長さで諦めるようにする
     * @param statementTimeout         文の実行：1 つの SQL が終わらないまま、スレッドと接続を使い続けないようにする。超えると 500
     * @param idleInTransactionTimeout トランザクション中のアイドル：トランザクションを開いたまま止まった接続のロックを解放する
     */
    @Validated
    @ConfigurationProperties("ecsite.db")
    record Properties(
            @DefaultValue("5s") @NotNull @DurationMin(millis = 1) Duration lockTimeout,
            @DefaultValue("30s") @NotNull @DurationMin(millis = 1) Duration statementTimeout,
            @DefaultValue("60s") @NotNull @DurationMin(millis = 1) Duration idleInTransactionTimeout) {

        /**
         * 接続の {@code options}（PostgreSQL のサーバーの設定）を作る。
         *
         * @return 3 つのタイムアウトを置く {@code options} の値
         */
        String options() {
            return "-c lock_timeout=" + lockTimeout.toMillis() + "ms"
                    + " -c statement_timeout=" + statementTimeout.toMillis() + "ms"
                    + " -c idle_in_transaction_session_timeout=" + idleInTransactionTimeout.toMillis() + "ms";
        }
    }
}

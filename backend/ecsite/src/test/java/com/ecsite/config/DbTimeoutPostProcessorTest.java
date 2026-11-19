package com.ecsite.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DB の接続のタイムアウトを接続のプールに置く後処理の単体テスト
 *
 * <p>プールは最初の接続の取得まで始まらないので、DB なしで確認する。アプリの接続に付くことは {@code DbTimeoutSettingsTest}（統合テスト）が確認する。
 */
class DbTimeoutPostProcessorTest {

    // ============================== 定数 ==============================

    /**
     * タイムアウトの設定の {@code options}
     */
    private static final String TIMEOUT_OPTIONS =
            "-c lock_timeout=500ms -c statement_timeout=2000ms -c idle_in_transaction_session_timeout=3000ms";

    // ============================== テスト ==============================

    @Test
    @DisplayName("接続の設定に options がないときは、3 つのタイムアウトだけを置く")
    void setsTimeoutOptionsWhenNoOptions() {
        // 準備：options のない接続のプール
        var dataSource = new HikariDataSource();

        // 操作：後処理を通す
        postProcessor().postProcessBeforeInitialization(dataSource, "dataSource");

        // 確認：タイムアウトだけの options
        assertThat(dataSource.getDataSourceProperties().get("options")).isEqualTo(TIMEOUT_OPTIONS);
    }

    @Test
    @DisplayName("接続の設定に options があるときは、既存の options を残し、後ろに空白でつないでタイムアウトを追加する")
    void keepsExistingOptionsAndAppendsTimeoutOptions() {
        // 準備：運用で options を渡した接続のプール
        var dataSource = new HikariDataSource();
        dataSource.addDataSourceProperty("options", "-c search_path=app");

        // 操作：後処理を通す
        postProcessor().postProcessBeforeInitialization(dataSource, "dataSource");

        // 確認：既存の options とタイムアウトの両方が残る
        assertThat(dataSource.getDataSourceProperties().get("options"))
                .isEqualTo("-c search_path=app " + TIMEOUT_OPTIONS);
    }

    @Test
    @DisplayName("接続のプールでない Bean は、変えずに返す")
    void returnsOtherBeanAsIs() {
        // 準備：接続のプールでない Bean
        var bean = new Object();

        // 操作と確認：後処理を通しても同じ Bean が返る
        assertThat(postProcessor().postProcessBeforeInitialization(bean, "other")).isSameAs(bean);
    }

    // ============================== プライベート ==============================

    /**
     * 3 つのタイムアウトを持つ設定で、後処理を作る。
     *
     * @return 後処理
     */
    private static BeanPostProcessor postProcessor() {
        var beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("properties", new DbTimeoutConfig.Properties(
                Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofSeconds(3)));
        return DbTimeoutConfig.dbTimeoutPostProcessor(beanFactory.getBeanProvider(DbTimeoutConfig.Properties.class));
    }
}

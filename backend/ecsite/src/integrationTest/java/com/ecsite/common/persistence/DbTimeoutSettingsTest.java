package com.ecsite.common.persistence;

import com.ecsite.support.config.IntegrationTest;
import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DB の接続のタイムアウトの設定の検証
 *
 * <p>設定ファイルの既定値が、アプリの接続に付くことを確認する。マイグレーション（Flyway）の接続には付かないことは {@code FlywayConnectionConfigTest}。
 */
@IntegrationTest
class DbTimeoutSettingsTest {

    // ============================== Bean ==============================

    /**
     * アプリの接続で設定を読む jOOQ
     */
    @Autowired
    DSLContext dsl;

    // ============================== テスト ==============================

    @Test
    @DisplayName("DC-556-1 アプリの接続には、ロック待ち 5 秒、文の実行 30 秒、トランザクション中のアイドル 1 分のタイムアウトがある")
    void appConnectionHasTimeouts() {
        // 操作：アプリの接続で、サーバーの設定を読む
        var lockTimeout = dsl.fetchSingle("SHOW lock_timeout").get(0, String.class);
        var statementTimeout = dsl.fetchSingle("SHOW statement_timeout").get(0, String.class);
        var idleTimeout = dsl.fetchSingle("SHOW idle_in_transaction_session_timeout").get(0, String.class);

        // 確認：既定値
        assertThat(lockTimeout).isEqualTo("5s");
        assertThat(statementTimeout).isEqualTo("30s");
        assertThat(idleTimeout).isEqualTo("1min");
    }
}

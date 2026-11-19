package com.ecsite.config;

import com.ecsite.support.TestSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.bind.BindException;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DB の接続のタイムアウトの設定の単体テスト
 *
 * <p>設定は Spring を起動せずに確認する。アプリの接続に付くことは {@code DbTimeoutSettingsTest}（統合テスト）が確認する。
 */
class DbTimeoutPropertiesTest {

    // ============================== 定数 ==============================

    /**
     * DB の接続のタイムアウトの設定の接頭辞
     */
    private static final String PREFIX = "ecsite.db";

    // ============================== テスト ==============================

    @Test
    @DisplayName("DC-556-1 既定は、ロック待ち 5 秒、文の実行 30 秒、トランザクション中のアイドル 60 秒")
    void defaults() {
        // 操作：設定ファイルを読んで当てはめる
        var properties = TestSettings.loadAndBind(PREFIX, DbTimeoutConfig.Properties.class);

        // 確認：既定の値
        assertThat(properties.lockTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.statementTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.idleInTransactionTimeout()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("接続の options は、3 つのタイムアウトをミリ秒で置く")
    void optionsHaveThreeTimeoutsInMilliseconds() {
        // 操作：3 つの値を変えて当てはめ、options を作る
        var properties = TestSettings.bind(PREFIX, DbTimeoutConfig.Properties.class,
                Map.of("lock-timeout", "500ms", "statement-timeout", "2m", "idle-in-transaction-timeout", "1h"));

        // 確認：PostgreSQL のサーバーの設定の名前に、ミリ秒の値
        assertThat(properties.options()).isEqualTo(
                "-c lock_timeout=500ms -c statement_timeout=120000ms -c idle_in_transaction_session_timeout=3600000ms");
    }

    @ParameterizedTest(name = "{0} = 「{1}」")
    @CsvSource({
            "lock-timeout, 0s",
            "lock-timeout, 999us",
            "statement-timeout, 0s",
            "statement-timeout, -1s",
            "idle-in-transaction-timeout, 0s",
            "idle-in-transaction-timeout, 999us"})
    @DisplayName("タイムアウトが 0、負の値、下限の 1ms 未満なら、値の検証で拒否される（0 はタイムアウトを無効にしてしまう）")
    void rejectsValueBelowMinimum(String name, String value) {
        // 操作と確認：起動のときと同じ検証で拒否される
        assertThatThrownBy(() -> TestSettings.bindValidated(PREFIX, DbTimeoutConfig.Properties.class, Map.of(name, value)))
                .isInstanceOf(BindException.class);
    }
}

package com.ecsite.module.payment.infrastructure.stripe;

import com.ecsite.support.TestSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.bind.BindException;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stripe の設定（StripeProperties）の単体テスト
 *
 * <p>Spring を起動せずに、設定の型へ値を当てはめて、時間の設定の下限を確認する。
 */
class StripePropertiesTest {

    // ============================== 定数 ==============================

    /**
     * 設定の接頭辞
     */
    private static final String PREFIX = "ecsite.payment.stripe";

    /**
     * 必須の値（テスト用の仮の鍵）
     */
    private static final Map<String, String> REQUIRED = Map.of(
            "secret-key", "sk_test_dummy", "webhook-secret", "whsec_dummy");

    // ============================== テスト ==============================

    @ParameterizedTest(name = "「{0}」が {1}")
    @CsvSource({
            "connect-timeout, 0s", "connect-timeout, -1s", "connect-timeout, 999us",
            "read-timeout, 0s", "read-timeout, -1s", "read-timeout, 999us"})
    @DisplayName("下限が 1ms の時間の設定が、0、負の値、1ms 未満（999us）なら、値の検証で拒否される")
    void rejectsDurationBelowMinimum(String name, String value) {
        // 準備：必須の値と、許す範囲の外の値
        var values = new HashMap<>(REQUIRED);
        values.put(name, value);

        // 操作と確認：値の検証で拒否される
        assertThatThrownBy(() -> TestSettings.bindValidated(PREFIX, StripeProperties.class, values))
                .isInstanceOf(BindException.class);
    }

    @ParameterizedTest(name = "「{0}」")
    @CsvSource({"connect-timeout", "read-timeout"})
    @DisplayName("下限が 1ms の時間の設定が、下限の 1ms なら、値の検証を通り、その値になる")
    void acceptsDurationAtMinimum(String name) {
        // 準備：必須の値と、1ms
        var values = new HashMap<>(REQUIRED);
        values.put(name, "1ms");

        // 操作：検証付きで当てはめる
        var properties = TestSettings.bindValidated(PREFIX, StripeProperties.class, values);

        // 確認：1ms になる
        var actual = name.equals("connect-timeout") ? properties.connectTimeout() : properties.readTimeout();
        assertThat(actual).isEqualTo(Duration.ofMillis(1));
    }
}

package com.ecsite.module.payment.infrastructure.stripe;

import com.ecsite.support.TestSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.bind.BindException;

import java.util.HashMap;
import java.util.Map;

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
            "connect-timeout, 0s", "connect-timeout, -1s",
            "read-timeout, 0s", "read-timeout, -1s"})
    @DisplayName("正の値だけの時間の設定が 0 以下なら、値の検証で拒否される")
    void rejectsNonPositiveDuration(String name, String value) {
        // 準備：必須の値と、下限の外の値
        var values = new HashMap<>(REQUIRED);
        values.put(name, value);

        // 操作と確認：値の検証で拒否される
        assertThatThrownBy(() -> TestSettings.bindValidated(PREFIX, StripeProperties.class, values))
                .isInstanceOf(BindException.class);
    }
}

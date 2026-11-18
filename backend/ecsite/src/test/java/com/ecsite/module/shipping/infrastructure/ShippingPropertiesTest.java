package com.ecsite.module.shipping.infrastructure;

import com.ecsite.support.TestSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 出荷の設定の検証
 *
 * <p>通知の発生日時の古さの上限と未来の許容の既定値と、下限（1ms 以上、null 不可）を確認する。
 */
class ShippingPropertiesTest {

    // ============================== 定数 ==============================

    /**
     * 設定の接頭辞
     */
    private static final String PREFIX = "ecsite.shipping";

    // ============================== テスト ==============================

    @Test
    @DisplayName("DC-548-4 古さの上限と未来の許容を指定しないと、既定の 30 日と 5 分になる")
    void usesDefaults() {
        // 操作：必須の値だけを検証付きで当てはめる
        var properties = TestSettings.bindValidated(PREFIX, ShippingProperties.class, Map.of("webhook-secret", "secret"));

        // 確認：既定値
        assertThat(properties.occurredAtMaxAge()).isEqualTo(Duration.ofDays(30));
        assertThat(properties.occurredAtFutureTolerance()).isEqualTo(Duration.ofMinutes(5));
    }

    @ParameterizedTest(name = "「{0}」が {1}")
    @CsvSource({
            "occurred-at-max-age, 0s", "occurred-at-max-age, -1s", "occurred-at-max-age, 999us",
            "occurred-at-future-tolerance, 0s", "occurred-at-future-tolerance, -1s", "occurred-at-future-tolerance, 999us"})
    @DisplayName("DC-548-4 下限が 1ms の時間の設定が、0、負の値、1ms 未満（999us）なら、値の検証で拒否される")
    void rejectsDurationBelowMinimum(String name, String value) {
        // 準備：必須の値と、許す範囲の外の値
        var values = new HashMap<>(Map.of("webhook-secret", "secret"));
        values.put(name, value);

        // 操作と確認：値の検証で拒否される
        assertThatThrownBy(() -> TestSettings.bindValidated(PREFIX, ShippingProperties.class, values))
                .isInstanceOf(BindException.class)
                .hasCauseInstanceOf(BindValidationException.class);
    }

    @ParameterizedTest(name = "「{0}」")
    @CsvSource({"occurred-at-max-age", "occurred-at-future-tolerance"})
    @DisplayName("DC-548-4 下限が 1ms の時間の設定が、下限の 1ms なら、値の検証を通り、その値になる")
    void acceptsDurationAtMinimum(String name) {
        // 操作：必須の値と 1ms を検証付きで当てはめる
        var values = new HashMap<>(Map.of("webhook-secret", "secret"));
        values.put(name, "1ms");
        var properties = TestSettings.bindValidated(PREFIX, ShippingProperties.class, values);

        // 確認：1ms になる
        var actual = name.equals("occurred-at-max-age") ? properties.occurredAtMaxAge() : properties.occurredAtFutureTolerance();
        assertThat(actual).isEqualTo(Duration.ofMillis(1));
    }
}

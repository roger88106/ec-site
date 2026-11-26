package com.ecsite.module.reporting.application;

import com.ecsite.support.TestSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.BindException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * レポートの設定の検証
 *
 * <p>会計年度の始まりの月は 1〜12 で、範囲の外は値の検証で拒否され、設定の名前が知らされることを確認する。
 */
class ReportingPropertiesTest {

    // ============================== 定数 ==============================

    /**
     * レポートの設定の接頭辞
     */
    private static final String PREFIX = "ecsite.reporting";

    // ============================== テスト ==============================

    @ParameterizedTest(name = "「{0}」")
    @ValueSource(ints = {1, 12})
    @DisplayName("会計年度の始まりの月が範囲の端なら、値の検証を通り、その月になる")
    void acceptsMonthAtBounds(int month) {
        // 操作：範囲の端の月を検証付きで当てはめる
        var properties = TestSettings.bindValidated(PREFIX, ReportingProperties.class,
                Map.of("fiscal-year-start-month", String.valueOf(month)));

        // 確認：設定した月になる
        assertThat(properties.fiscalYearStartMonth()).isEqualTo(month);
    }

    @ParameterizedTest(name = "「{0}」")
    @ValueSource(ints = {0, 13})
    @DisplayName("会計年度の始まりの月が範囲の外なら、値の検証で拒否され、fiscalYearStartMonth が知らされる")
    void rejectsMonthOutOfBounds(int month) {
        // 操作と確認：値の検証で拒否され、原因に設定の名前がある
        assertThatThrownBy(() -> TestSettings.bindValidated(PREFIX, ReportingProperties.class,
                Map.of("fiscal-year-start-month", String.valueOf(month))))
                .isInstanceOf(BindException.class)
                .rootCause().hasMessageContaining("fiscalYearStartMonth");
    }
}

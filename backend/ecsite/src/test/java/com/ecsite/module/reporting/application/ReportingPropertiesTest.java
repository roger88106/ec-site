package com.ecsite.module.reporting.application;

import com.ecsite.support.TestSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * レポートの設定の検証
 *
 * <p>会計年度の始まりの月は 1〜12 で、範囲の外は値の検証で拒否され、設定の名前が知らされることを確認する。
 * 上限と下限は、それぞれ別のテストにする。
 */
class ReportingPropertiesTest {

    // ============================== 定数 ==============================

    /**
     * レポートの設定の接頭辞
     */
    private static final String PREFIX = "ecsite.reporting";

    /**
     * 会計年度の始まりの月の下限（範囲の端）
     */
    private static final int MONTH_MIN = 1;

    /**
     * 会計年度の始まりの月の上限（範囲の端）
     */
    private static final int MONTH_MAX = 12;

    // ============================== テスト ==============================

    @Test
    @DisplayName("会計年度の始まりの月が下限の 1 月なら、値の検証を通り、その月になる")
    void acceptsMonthAtMinimum() {
        // 操作：下限の月を検証付きで当てはめる
        var properties = bind(MONTH_MIN);

        // 確認：設定した月になる
        assertThat(properties.fiscalYearStartMonth()).isEqualTo(MONTH_MIN);
    }

    @Test
    @DisplayName("会計年度の始まりの月が上限の 12 月なら、値の検証を通り、その月になる")
    void acceptsMonthAtMaximum() {
        // 操作：上限の月を検証付きで当てはめる
        var properties = bind(MONTH_MAX);

        // 確認：設定した月になる
        assertThat(properties.fiscalYearStartMonth()).isEqualTo(MONTH_MAX);
    }

    @Test
    @DisplayName("会計年度の始まりの月が下限の 1 月より小さい 0 なら、値の検証で拒否され、fiscalYearStartMonth が知らされる")
    void rejectsMonthBelowMinimum() {
        // 操作と確認：値の検証で拒否され、原因に設定の名前がある
        assertThatThrownBy(() -> bind(MONTH_MIN - 1))
                .isInstanceOf(BindException.class)
                .rootCause().hasMessageContaining("fiscalYearStartMonth");
    }

    @Test
    @DisplayName("会計年度の始まりの月が上限の 12 月より大きい 13 なら、値の検証で拒否され、fiscalYearStartMonth が知らされる")
    void rejectsMonthAboveMaximum() {
        // 操作と確認：値の検証で拒否され、原因に設定の名前がある
        assertThatThrownBy(() -> bind(MONTH_MAX + 1))
                .isInstanceOf(BindException.class)
                .rootCause().hasMessageContaining("fiscalYearStartMonth");
    }

    /**
     * 会計年度の始まりの月を検証付きで当てはめる。
     *
     * @param month 月
     * @return 当てはめた設定
     */
    private static ReportingProperties bind(int month) {
        return TestSettings.bindValidated(PREFIX, ReportingProperties.class,
                Map.of("fiscal-year-start-month", String.valueOf(month)));
    }
}

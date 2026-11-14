package com.ecsite.module.reporting.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * レポートの設定を、設定の値から view にする操作の単体テスト
 */
class ReportSettingsQueryServiceTest {

    // ============================== 定数 ==============================

    /**
     * 会計年度の始まりの月
     *
     * <p>既定の 4 と違う値にして、設定の値を使うことを確認する。
     */
    private static final int FISCAL_YEAR_START_MONTH = 10;

    // ============================== テスト ==============================

    @Test
    @DisplayName("レポートの設定は、設定の会計年度の始まりの月を view にして返す")
    void returnsFiscalYearStartMonthFromProperties() {
        // 準備：会計年度が 10 月始まりの設定
        var service = new ReportSettingsQueryService(new ReportingProperties(FISCAL_YEAR_START_MONTH));

        // 操作：レポートの設定を求める
        var settings = service.settings();

        // 確認：設定の月のまま
        assertThat(settings.fiscalYearStartMonth()).isEqualTo(FISCAL_YEAR_START_MONTH);
    }
}

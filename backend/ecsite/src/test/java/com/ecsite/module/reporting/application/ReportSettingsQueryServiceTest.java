package com.ecsite.module.reporting.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * レポートの設定を、設定の値から view にする操作の単体テスト
 */
class ReportSettingsQueryServiceTest {

    @Test
    @DisplayName("レポートの設定は、設定の会計年度の始まりの月を view にして返す")
    void returnsFiscalYearStartMonthFromProperties() {
        var service = new ReportSettingsQueryService(new ReportingProperties(10));

        assertThat(service.settings().fiscalYearStartMonth()).isEqualTo(10);
    }
}

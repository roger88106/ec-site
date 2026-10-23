package com.ecsite.module.reporting.application.model.view;

/**
 * レポートの設定
 *
 * @param fiscalYearStartMonth 会計年度の始まりの月（1〜12）
 */
public record ReportSettingsView(int fiscalYearStartMonth) {
}

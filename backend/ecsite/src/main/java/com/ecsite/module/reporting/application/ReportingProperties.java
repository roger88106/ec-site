package com.ecsite.module.reporting.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * レポートの設定。環境変数で上書きする（例：ECSITE_REPORTING_FISCAL_YEAR_START_MONTH）。
 *
 * @param fiscalYearStartMonth 会計年度の始まりの月（1〜12）。売上の推移の四半期と年の区切りに使う
 */
@Validated
@ConfigurationProperties("ecsite.reporting")
public record ReportingProperties(
        @DefaultValue("4") @Min(1) @Max(12) int fiscalYearStartMonth) {
}

package com.ecsite.module.tax.api;

import com.ecsite.common.model.value.Money;

import java.math.BigDecimal;

/**
 * 1 つの税率の消費税額（spec 002 BR-8）。
 *
 * @param ratePercent   率（%）
 * @param taxableAmount その率の対象の税込金額の合計
 * @param taxAmount     消費税額
 */
public record ConsumptionTaxAmount(BigDecimal ratePercent, Money taxableAmount, Money taxAmount) {
}

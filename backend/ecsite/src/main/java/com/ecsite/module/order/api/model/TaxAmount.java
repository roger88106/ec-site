package com.ecsite.module.order.api.model;

import com.ecsite.common.model.value.Money;

import java.math.BigDecimal;

/**
 * 1 つの税率の消費税額
 *
 * @param ratePercent   率（%）
 * @param taxableAmount 対象額（税込）
 * @param taxAmount     消費税額
 */
public record TaxAmount(BigDecimal ratePercent, Money taxableAmount, Money taxAmount) {
}

package com.ecsite.module.tax.api.model;

import com.ecsite.common.model.value.Money;

import java.math.BigDecimal;

/**
 * 消費税額の計算の対象
 *
 * <p>品目の小計や送料などの税込金額を、その率と組にしたもの。
 *
 * @param ratePercent   率（%）
 * @param amountInclTax 税込金額
 */
public record TaxableAmount(BigDecimal ratePercent, Money amountInclTax) {
}

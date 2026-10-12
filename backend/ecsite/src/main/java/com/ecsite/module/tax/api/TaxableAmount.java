package com.ecsite.module.tax.api;

import com.ecsite.common.model.Money;

import java.math.BigDecimal;

/**
 * 消費税額の計算の対象（spec 002 BR-8）。品目の小計や送料を、その率と組にして渡す。
 *
 * @param ratePercent   率（%）
 * @param amountInclTax 税込金額
 */
public record TaxableAmount(BigDecimal ratePercent, Money amountInclTax) {
}

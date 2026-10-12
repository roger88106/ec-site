package com.ecsite.module.order.domain.quote;

import com.ecsite.common.model.value.Money;
import org.jmolecules.ddd.annotation.ValueObject;

import java.math.BigDecimal;

/**
 * 1 つの税率の消費税額（BR-8）
 *
 * @param ratePercent   率（%）
 * @param taxableAmount その率の対象の税込金額（小計と送料）の合計
 * @param taxAmount     消費税額
 */
@ValueObject
public record TaxAmount(BigDecimal ratePercent, Money taxableAmount, Money taxAmount) {
}

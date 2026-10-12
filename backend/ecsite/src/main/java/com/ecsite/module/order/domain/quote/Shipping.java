package com.ecsite.module.order.domain.quote;

import com.ecsite.common.model.Money;
import org.jmolecules.ddd.annotation.ValueObject;

import java.math.BigDecimal;

/**
 * 送料（BR-6）
 *
 * @param fee         送料（税込）
 * @param ratePercent 送料に適用する率（%。標準税率）
 */
@ValueObject
public record Shipping(Money fee, BigDecimal ratePercent) {
}

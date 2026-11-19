package com.ecsite.module.payment.api.model;

import com.ecsite.common.model.value.Money;

/**
 * 支払 1 回の金額の範囲
 *
 * @param min 下限（この金額を含む）
 * @param max 上限（この金額を含む）
 */
public record AmountRange(Money min, Money max) {
}

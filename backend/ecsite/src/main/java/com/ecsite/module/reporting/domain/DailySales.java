package com.ecsite.module.reporting.domain;

import com.ecsite.common.model.value.Money;

/**
 * 1 日の売上額と販売数量
 *
 * @param amount   売上額
 * @param quantity 販売数量
 */
public record DailySales(Money amount, long quantity) {
}

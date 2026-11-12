package com.ecsite.module.reporting.domain.sales;

import com.ecsite.common.model.value.Money;

/**
 * 売上の推移の区切りの売上
 *
 * @param bucket   区切り
 * @param amount   売上額
 * @param quantity 販売数量
 */
public record TrendPoint(TrendBucket bucket, Money amount, long quantity) {
}

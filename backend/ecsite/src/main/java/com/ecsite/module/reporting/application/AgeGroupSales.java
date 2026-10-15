package com.ecsite.module.reporting.application;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.reporting.domain.AgeGroup;

/**
 * 年齢層の売上
 *
 * @param ageGroup 年齢層
 * @param amount   売上額
 * @param orders   注文件数
 */
public record AgeGroupSales(AgeGroup ageGroup, Money amount, long orders) {
}

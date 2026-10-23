package com.ecsite.module.reporting.application.model.view;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.reporting.domain.sales.ReportGender;

/**
 * 性別の売上
 *
 * @param gender 性別の区分
 * @param amount 売上額
 * @param orders 注文件数
 */
public record GenderSales(ReportGender gender, Money amount, long orders) {
}

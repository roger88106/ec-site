package com.ecsite.module.reporting.application.model.view;

import com.ecsite.module.reporting.domain.TrendPoint;

import java.util.List;

/**
 * 売上の推移
 *
 * @param points           区切りごとの売上額と販売数量（期間のすべての区切り）
 * @param shippingExcluded 送料を除いたか（指定したとき、または商品を選んだとき）
 */
public record SalesTrendReport(List<TrendPoint> points, boolean shippingExcluded) {
}

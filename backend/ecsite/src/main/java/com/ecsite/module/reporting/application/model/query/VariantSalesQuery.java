package com.ecsite.module.reporting.application.model.query;

import com.ecsite.module.reporting.domain.Basis;
import com.ecsite.module.reporting.domain.ReportPeriod;

import java.util.UUID;

/**
 * バリアントの売れ行きの条件
 *
 * @param productId  商品 ID
 * @param period     期間
 * @param basis      売上の数え方
 * @param excludeTax 消費税を除くか
 */
public record VariantSalesQuery(UUID productId, ReportPeriod period, Basis basis, boolean excludeTax) {
}

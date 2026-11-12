package com.ecsite.module.reporting.application.model.query;

import com.ecsite.module.reporting.domain.sales.Basis;
import com.ecsite.module.reporting.domain.sales.ReportPeriod;
import lombok.NonNull;

import java.util.UUID;

/**
 * バリアントの売れ行きの条件
 *
 * @param productId  商品 ID
 * @param period     期間
 * @param basis      売上の数え方
 * @param excludeTax 消費税を除くか
 */
public record VariantSalesQuery(@NonNull UUID productId, @NonNull ReportPeriod period, @NonNull Basis basis, boolean excludeTax) {
}

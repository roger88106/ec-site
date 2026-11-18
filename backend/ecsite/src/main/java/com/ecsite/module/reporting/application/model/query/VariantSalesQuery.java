package com.ecsite.module.reporting.application.model.query;

import com.ecsite.module.reporting.domain.sales.Basis;
import com.ecsite.module.reporting.domain.sales.ReportPeriod;
import com.ecsite.module.reporting.domain.sales.SalesScope;
import lombok.NonNull;

/**
 * バリアントの売れ行きの条件
 *
 * @param period 期間
 * @param basis  売上の数え方
 * @param scope  集計の範囲（商品を選ぶ範囲）
 */
public record VariantSalesQuery(@NonNull ReportPeriod period, @NonNull Basis basis, @NonNull SalesScope scope) {
}

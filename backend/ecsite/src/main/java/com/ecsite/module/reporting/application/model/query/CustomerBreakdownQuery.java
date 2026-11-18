package com.ecsite.module.reporting.application.model.query;

import com.ecsite.module.reporting.domain.sales.Basis;
import com.ecsite.module.reporting.domain.sales.ReportPeriod;
import com.ecsite.module.reporting.domain.sales.SalesScope;
import lombok.NonNull;

/**
 * 地域・年齢層・性別の条件
 *
 * @param period 期間
 * @param basis  売上の数え方
 * @param scope  集計の範囲
 */
public record CustomerBreakdownQuery(@NonNull ReportPeriod period, @NonNull Basis basis, @NonNull SalesScope scope) {
}

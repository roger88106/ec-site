package com.ecsite.module.reporting.application.model.query;

import com.ecsite.module.reporting.domain.sales.Basis;
import com.ecsite.module.reporting.domain.sales.Granularity;
import com.ecsite.module.reporting.domain.sales.ReportPeriod;
import com.ecsite.module.reporting.domain.sales.SalesScope;
import lombok.NonNull;

/**
 * 売上の推移の条件
 *
 * @param period      期間
 * @param granularity 粒度
 * @param basis       売上の数え方
 * @param scope       集計の範囲
 */
public record SalesTrendQuery(@NonNull ReportPeriod period, @NonNull Granularity granularity, @NonNull Basis basis,
                              @NonNull SalesScope scope) {
}

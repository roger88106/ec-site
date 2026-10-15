package com.ecsite.module.reporting.application;

import com.ecsite.module.reporting.application.port.ReportQueryRepository;
import com.ecsite.module.reporting.domain.SalesTrend;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 売上の推移（docs/backend/modules/reporting.md「売上の推移」）。<br />
 * 日ごとに SQL で集計し、区切りへのまとめは Java で行う。
 */
@Service
@RequiredArgsConstructor
public class SalesTrendQueryService {

    /**
     * 写しを集計する問い合わせ
     */
    private final ReportQueryRepository queryRepository;

    /**
     * レポートの設定（会計年度の始まりの月）
     */
    private final ReportingProperties properties;

    /**
     * 売上の推移を求める。
     *
     * @param query 条件
     * @return 区切りごとの売上
     */
    @Transactional(readOnly = true)
    public SalesTrendReport trend(SalesTrendQuery query) {
        var daily = queryRepository.dailySales(query.period(), query.basis(), query.productId(), query.excludeTax(),
                query.excludeShipping());
        var buckets = query.period().split(query.granularity(), properties.fiscalYearStartMonth());
        return new SalesTrendReport(SalesTrend.sum(buckets, daily), query.excludeShipping() || query.productId() != null);
    }
}

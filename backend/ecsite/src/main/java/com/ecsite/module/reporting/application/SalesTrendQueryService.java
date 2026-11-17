package com.ecsite.module.reporting.application;

import com.ecsite.module.reporting.application.model.query.SalesTrendQuery;
import com.ecsite.module.reporting.application.model.view.SalesTrendReport;
import com.ecsite.module.reporting.application.port.ReportQueryRepository;
import com.ecsite.module.reporting.domain.sales.SalesTrend;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 売上の推移のクエリサービス
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
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
    public SalesTrendReport trend(@NonNull SalesTrendQuery query) {
        // 日ごとの集計
        var daily = queryRepository.dailySales(query.period(), query.basis(), query.productId(), query.excludeTax(),
                query.excludeShipping());
        // 区切りへのまとめ
        var buckets = query.period().split(query.granularity(), properties.fiscalYearStartMonth());
        // 商品を選んだときは、指定によらず送料を除いている
        return new SalesTrendReport(SalesTrend.sum(buckets, daily), query.excludeShipping() || query.productId() != null);
    }
}

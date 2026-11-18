package com.ecsite.module.reporting.application.port;

import com.ecsite.module.reporting.application.model.SegmentTotal;
import com.ecsite.module.reporting.application.model.VariantTotal;
import com.ecsite.module.reporting.domain.sales.Basis;
import com.ecsite.module.reporting.domain.sales.DailySales;
import com.ecsite.module.reporting.domain.sales.ReportPeriod;
import com.ecsite.module.reporting.domain.sales.SalesScope;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 写しを集計する問い合わせ
 *
 * <p>数える注文と日は売上の数え方で決める。商品を指定したら、その商品の明細だけを数え、送料は含めない。
 */
public interface ReportQueryRepository {

    /**
     * 日ごとの売上額と販売数量を求める。
     *
     * @param period 期間
     * @param basis  売上の数え方
     * @param scope  集計の範囲
     * @return 日 → 売上。売れなかった日は含めない
     */
    Map<LocalDate, DailySales> dailySales(ReportPeriod period, Basis basis, SalesScope scope);

    /**
     * 商品のバリアントごとの販売数量と売上額を求める。
     *
     * <p>送料は含めない。
     *
     * @param period 期間
     * @param basis  売上の数え方
     * @param scope  集計の範囲（商品を選んだ範囲）
     * @return 売れたバリアントの売上
     */
    List<VariantTotal> variantSales(ReportPeriod period, Basis basis, SalesScope scope);

    /**
     * 都道府県・年齢・性別の組ごとの売上額と注文件数を求める。
     *
     * @param period 期間
     * @param basis  売上の数え方
     * @param scope  集計の範囲
     * @return 組ごとの売上
     */
    List<SegmentTotal> segmentSales(ReportPeriod period, Basis basis, SalesScope scope);
}

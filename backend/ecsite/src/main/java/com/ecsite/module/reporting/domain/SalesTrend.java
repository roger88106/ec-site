package com.ecsite.module.reporting.domain;

import com.ecsite.common.model.value.Money;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 売上の推移の、区切りへのまとめ（docs/backend/modules/reporting.md「売上の推移」）
 */
public final class SalesTrend {

    private SalesTrend() {
    }

    /**
     * 日ごとの売上を区切りにまとめる。売れなかった区切りも 0 として並べる。
     *
     * @param buckets 区切り（期間のすべて）
     * @param daily   日 → その日の売上
     * @return 区切りの順の売上
     */
    public static List<TrendPoint> sum(List<TrendBucket> buckets, Map<LocalDate, DailySales> daily) {
        return buckets.stream().map(bucket -> {
            var amount = Money.ZERO;
            long quantity = 0;
            for (var entry : daily.entrySet()) {
                if (bucket.contains(entry.getKey())) {
                    amount = amount.plus(entry.getValue().amount());
                    quantity += entry.getValue().quantity();
                }
            }
            return new TrendPoint(bucket, amount, quantity);
        }).toList();
    }
}

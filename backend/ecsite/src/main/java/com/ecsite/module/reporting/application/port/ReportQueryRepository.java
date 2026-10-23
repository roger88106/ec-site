package com.ecsite.module.reporting.application.port;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.reporting.domain.Basis;
import com.ecsite.module.reporting.domain.DailySales;
import com.ecsite.module.reporting.domain.ReportPeriod;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 写しを集計する問い合わせ（docs/design/modules/reporting.md「集計」）。<br />
 * 数える注文と日は売上の数え方で決める。商品を指定したら、その商品の明細だけを数え、送料は含めない。
 */
public interface ReportQueryRepository {

    /**
     * 日ごとの売上額と販売数量を求める。
     *
     * @param period          期間
     * @param basis           売上の数え方
     * @param productId       商品 ID。全商品なら null
     * @param excludeTax      消費税を除くか
     * @param excludeShipping 送料を除くか（商品を指定したときは使わない）
     * @return 日 → 売上。売れなかった日は含めない
     */
    Map<LocalDate, DailySales> dailySales(ReportPeriod period, Basis basis, @Nullable UUID productId, boolean excludeTax,
                                          boolean excludeShipping);

    /**
     * 商品のバリアントごとの販売数量と売上額を求める（送料は含めない）。
     *
     * @param period     期間
     * @param basis      売上の数え方
     * @param productId  商品 ID
     * @param excludeTax 消費税を除くか
     * @return 売れたバリアントの売上
     */
    List<VariantTotal> variantSales(ReportPeriod period, Basis basis, UUID productId, boolean excludeTax);

    /**
     * 都道府県・年齢・性別の組ごとの売上額と注文件数を求める。
     *
     * @param period          期間
     * @param basis           売上の数え方
     * @param productId       商品 ID。全商品なら null
     * @param excludeTax      消費税を除くか
     * @param excludeShipping 送料を除くか（商品を指定したときは使わない）
     * @return 組ごとの売上
     */
    List<SegmentTotal> segmentSales(ReportPeriod period, Basis basis, @Nullable UUID productId, boolean excludeTax,
                                    boolean excludeShipping);

    /**
     * バリアントの売上
     *
     * @param variantId   バリアント ID
     * @param variantCode バリアント品番の写し
     * @param quantity    販売数量
     * @param amount      売上額
     */
    record VariantTotal(UUID variantId, String variantCode, long quantity, Money amount) {
    }

    /**
     * 都道府県・年齢・性別の組の売上
     *
     * @param prefectureCode 都道府県のコード。不明なら null
     * @param age            注文時の年齢。不明なら null
     * @param gender         注文時の性別。不明なら null
     * @param amount         売上額
     * @param orders         注文件数
     */
    record SegmentTotal(@Nullable String prefectureCode, @Nullable Integer age, @Nullable String gender, Money amount,
                        long orders) {
    }
}

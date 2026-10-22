package com.ecsite.module.tax.api;

import com.ecsite.module.tax.domain.taxcategory.vo.EffectivePeriod;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 税区分ごとの税率の一覧（予定を含む）。<br />
 * 商品カタログが、表示時刻で有効な率を決めて SQL に埋め込むために使う（docs/design/modules/catalog.md）。
 *
 * @param taxCategoryId 税区分 ID
 * @param rates         税率の一覧（適用開始日時の古い順）
 */
public record TaxRateSchedule(UUID taxCategoryId, List<Rate> rates) {

    /**
     * 指定した時点の率を返す。
     *
     * @param instant 時点
     * @return その時点を含む税率の率（%）。なければ空
     */
    public Optional<BigDecimal> rateAt(Instant instant) {
        return EffectivePeriod.findAt(rates, Rate::period, instant).map(Rate::ratePercent);
    }

    /**
     * 税率。適用期間は開始を含み、終了を含まない。
     *
     * @param ratePercent   率（%）
     * @param effectiveFrom 適用開始日時。null は過去すべて
     * @param effectiveTo   適用終了日時。null は無期限
     */
    public record Rate(BigDecimal ratePercent, @Nullable Instant effectiveFrom, @Nullable Instant effectiveTo) {

        /**
         * 税率の適用期間を返す。
         *
         * @return 適用期間
         */
        EffectivePeriod period() {
            return EffectivePeriod.of(effectiveFrom, effectiveTo);
        }
    }
}

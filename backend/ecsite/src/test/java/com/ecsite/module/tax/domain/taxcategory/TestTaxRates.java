package com.ecsite.module.tax.domain.taxcategory;

import com.ecsite.module.tax.domain.taxcategory.vo.EffectivePeriod;
import com.ecsite.module.tax.domain.taxcategory.vo.RatePercent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 税率のテストで使う、税率と置き換えの要素の作り方。単体テストと統合テストが共有する。
 */
public final class TestTaxRates {

    /**
     * インスタンスを作らない。
     */
    private TestTaxRates() {
    }

    /**
     * 税率を作る（ID は新しく振る）。期間の端の null は無限
     *
     * @param percent 税率（%）
     * @param from    適用の開始
     * @param to      適用の終了
     * @return 税率
     */
    public static TaxRate rate(String percent, Instant from, Instant to) {
        return new TaxRate(UUID.randomUUID(), RatePercent.of(new BigDecimal(percent)), EffectivePeriod.of(from, to));
    }

    /**
     * 新しい税率（ID なし）の置き換えの要素を作る
     *
     * @param percent 税率（%）
     * @param from    適用の開始
     * @param to      適用の終了
     * @return 置き換えの要素
     */
    public static TaxRateDraft newDraft(String percent, Instant from, Instant to) {
        return draft(null, percent, from, to);
    }

    /**
     * 置き換えの要素を作る。既存の税率を残すなら、その ID を渡す
     *
     * @param id      既存の税率の ID（新しい税率なら null）
     * @param percent 税率（%）
     * @param from    適用の開始
     * @param to      適用の終了
     * @return 置き換えの要素
     */
    public static TaxRateDraft draft(UUID id, String percent, Instant from, Instant to) {
        return new TaxRateDraft(id, RatePercent.of(new BigDecimal(percent)), EffectivePeriod.of(from, to));
    }
}

package com.ecsite.module.catalog.infrastructure;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jooq.CaseWhenStep;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static com.ecsite.jooq.catalog.Tables.PRODUCT;

/**
 * 税込価格を計算する SQL の式（BR-1、docs/modules/catalog.md）。<br />
 * 税区分ごとの率を埋め込み、{@code floor(税抜価格 × (100 + 率) ÷ 100)} を計算する。1 円未満は切り捨て。
 * 税モジュール（Java）の計算と同じ結果になることは、統合テストで確かめる。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PriceInclTaxField {

    /**
     * 税込価格の式を作る。率のない税区分の商品は null になる。
     *
     * @param rates 税区分 ID → 率（%）。空でないこと
     * @return 税込価格（円）の式
     */
    static Field<Long> of(Map<UUID, BigDecimal> rates) {
        Field<BigDecimal> rate = rateField(rates);
        return DSL.floor(PRODUCT.PRICE_EXCL_TAX.cast(SQLDataType.NUMERIC)
                        .mul(rate.add(DSL.inline(BigDecimal.valueOf(100))))
                        .div(DSL.inline(BigDecimal.valueOf(100))))
                .cast(SQLDataType.BIGINT);
    }

    /**
     * 商品の税区分の率の式（CASE 式）。
     *
     * @param rates 税区分 ID → 率（%）
     * @return 率（%）の式
     */
    private static Field<BigDecimal> rateField(Map<UUID, BigDecimal> rates) {
        // 税区分 ID ごとに WHEN を連ねる。どれにも当たらない（有効な税率のない）税区分は null になる
        CaseWhenStep<UUID, BigDecimal> step = null;
        for (var entry : rates.entrySet()) {
            var key = DSL.inline(entry.getKey());
            var value = DSL.inline(entry.getValue(), SQLDataType.NUMERIC);
            step = step == null
                    ? DSL.choose(PRODUCT.TAX_CATEGORY_ID).when(key, value)
                    : step.when(key, value);
        }
        if (step == null) {
            throw new IllegalArgumentException("税区分ごとの率が空です");
        }
        return step;
    }
}

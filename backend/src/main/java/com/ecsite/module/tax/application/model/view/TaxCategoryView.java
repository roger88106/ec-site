package com.ecsite.module.tax.application.model.view;

import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 税区分のビュー
 *
 * @param id                 税区分 ID
 * @param name               名称
 * @param currentRatePercent 現時点で有効な率。なければ null
 * @param rates              税率の一覧（適用開始日時の古い順）
 * @param version            バージョン
 */
public record TaxCategoryView(UUID id, String name, @Nullable BigDecimal currentRatePercent,
                              List<TaxRateView> rates, long version) {

    /**
     * 税率のビュー
     *
     * @param id            税率 ID
     * @param ratePercent   率（%）
     * @param effectiveFrom 適用開始日時。null は過去すべて
     * @param effectiveTo   適用終了日時。null は無期限
     */
    public record TaxRateView(UUID id, BigDecimal ratePercent,
                              @Nullable Instant effectiveFrom, @Nullable Instant effectiveTo) {
    }
}

package com.ecsite.module.tax.application.model.command;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 税率の一覧の置き換えコマンド
 *
 * @param taxCategoryId 税区分 ID
 * @param version       取得時のバージョン
 * @param rates         置き換え後の税率の一覧
 */
public record ReplaceTaxRatesCommand(UUID taxCategoryId, long version, List<Rate> rates) {

    /**
     * 置き換え後の税率の 1 件
     *
     * @param id            既存の税率を残す場合はその ID。null は新しい税率
     * @param ratePercent   率（%）
     * @param effectiveFrom 適用開始日時。null は過去すべて
     * @param effectiveTo   適用終了日時。null は無期限
     */
    public record Rate(@Nullable UUID id, BigDecimal ratePercent,
                       @Nullable Instant effectiveFrom, @Nullable Instant effectiveTo) {
    }
}

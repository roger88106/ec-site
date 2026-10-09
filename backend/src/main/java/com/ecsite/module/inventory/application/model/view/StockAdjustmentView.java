package com.ecsite.module.inventory.application.model.view;

import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * 在庫調整のビュー（履歴の 1 件）
 *
 * @param id            在庫調整 ID
 * @param delta         増減量
 * @param reason        調整理由（{@code ARRIVAL} / {@code STOCKTAKE} / {@code DAMAGE_OR_LOSS} / {@code OTHER}）
 * @param memo          メモ。なければ null
 * @param quantityAfter 調整後の在庫数
 * @param adjustedAt    調整日時
 */
public record StockAdjustmentView(UUID id, int delta, String reason, @Nullable String memo, int quantityAfter,
                                  Instant adjustedAt) {
}

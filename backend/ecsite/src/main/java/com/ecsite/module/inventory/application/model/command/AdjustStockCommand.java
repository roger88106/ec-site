package com.ecsite.module.inventory.application.model.command;

import com.ecsite.common.model.value.Operator;
import jakarta.annotation.Nullable;

import java.util.UUID;

/**
 * 在庫の調整コマンド
 *
 * @param variantId バリアント ID
 * @param delta     増減量
 * @param reason    調整理由（{@code ARRIVAL} / {@code STOCKTAKE} / {@code DAMAGE_OR_LOSS} / {@code OTHER}）
 * @param memo      メモ。理由が「その他」なら必須
 * @param operator  調整した運営者
 */
public record AdjustStockCommand(UUID variantId, int delta, String reason, @Nullable String memo, Operator operator) {
}

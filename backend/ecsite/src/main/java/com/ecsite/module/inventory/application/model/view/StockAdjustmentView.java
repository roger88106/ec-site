package com.ecsite.module.inventory.application.model.view;

import com.ecsite.module.inventory.domain.stock.StockAdjustmentReason;
import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * 在庫調整のビュー（履歴の 1 件）
 *
 * @param id            在庫調整 ID
 * @param delta         増減量
 * @param reason        調整理由
 * @param memo          メモ。なければ null
 * @param orderId       注文 ID。注文に由来する調整でなければ null
 * @param orderNumber   注文番号（写し）。注文に由来する調整でなければ null
 * @param quantityAfter 調整後の在庫数
 * @param adjustedAt    調整日時
 * @param operatorName  調整した運営者の氏名（写し）。運営者の調整でなければ（記録を始める前も）null
 */
public record StockAdjustmentView(UUID id, int delta, StockAdjustmentReason reason, @Nullable String memo,
                                  @Nullable UUID orderId, @Nullable String orderNumber, int quantityAfter,
                                  Instant adjustedAt, @Nullable String operatorName) {
}

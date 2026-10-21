package com.ecsite.module.catalog.event;

import com.ecsite.common.model.base.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * バリアント削除。在庫モジュールが、その在庫と調整履歴を消す。
 *
 * @param eventId    イベント ID
 * @param occurredAt 発生時刻
 * @param productId  商品 ID
 * @param variantId  バリアント ID
 */
public record VariantDeleted(UUID eventId, Instant occurredAt, UUID productId, UUID variantId) implements DomainEvent {

    /**
     * イベントを作る。
     *
     * @param occurredAt 発生時刻
     * @param productId  商品 ID
     * @param variantId  バリアント ID
     * @return イベント
     */
    public static VariantDeleted of(Instant occurredAt, UUID productId, UUID variantId) {
        return new VariantDeleted(UUID.randomUUID(), occurredAt, productId, variantId);
    }
}

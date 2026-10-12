package com.ecsite.module.catalog.event;

import com.ecsite.common.model.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * バリアント登録。在庫モジュールが、在庫数 0 の在庫を作る。
 *
 * @param eventId    イベント ID
 * @param occurredAt 発生時刻
 * @param productId  商品 ID
 * @param variantId  バリアント ID
 */
public record VariantRegistered(UUID eventId, Instant occurredAt, UUID productId, UUID variantId) implements DomainEvent {

    /**
     * イベントを作る。
     *
     * @param occurredAt 発生時刻
     * @param productId  商品 ID
     * @param variantId  バリアント ID
     * @return イベント
     */
    public static VariantRegistered of(Instant occurredAt, UUID productId, UUID variantId) {
        return new VariantRegistered(UUID.randomUUID(), occurredAt, productId, variantId);
    }
}

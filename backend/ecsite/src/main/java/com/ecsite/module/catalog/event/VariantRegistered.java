package com.ecsite.module.catalog.event;

import com.ecsite.common.model.base.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * バリアント登録
 *
 * <p>バリアントを登録したときに発行する。
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

package com.ecsite.module.catalog.event;

import com.ecsite.common.model.base.DomainEvent;
import com.ecsite.common.model.id.ProductId;
import com.ecsite.common.model.id.VariantId;

import java.time.Instant;
import java.util.UUID;

/**
 * バリアント削除
 *
 * <p>バリアントを削除したときに発行する。
 *
 * @param eventId    イベント ID
 * @param occurredAt 発生時刻
 * @param productId  商品 ID
 * @param variantId  バリアント ID
 */
public record VariantDeleted(UUID eventId, Instant occurredAt, ProductId productId, VariantId variantId) implements DomainEvent {

    /**
     * イベントを作る。
     *
     * @param occurredAt 発生時刻
     * @param productId  商品 ID
     * @param variantId  バリアント ID
     * @return イベント
     */
    public static VariantDeleted of(Instant occurredAt, ProductId productId, VariantId variantId) {
        return new VariantDeleted(DomainEvent.newEventId(), occurredAt, productId, variantId);
    }
}

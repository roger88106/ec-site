package com.ecsite.module.catalog.event;

import com.ecsite.common.model.base.DomainEvent;
import com.ecsite.common.model.id.ProductId;

import java.time.Instant;
import java.util.UUID;

/**
 * 商品削除
 *
 * <p>商品を削除したときに発行する。
 *
 * @param eventId    イベント ID
 * @param occurredAt 発生時刻
 * @param productId  商品 ID
 */
public record ProductDeleted(UUID eventId, Instant occurredAt, ProductId productId) implements DomainEvent {

    /**
     * イベントを作る。
     *
     * @param occurredAt 発生時刻
     * @param productId  商品 ID
     * @return イベント
     */
    public static ProductDeleted of(Instant occurredAt, ProductId productId) {
        return new ProductDeleted(DomainEvent.newEventId(), occurredAt, productId);
    }
}

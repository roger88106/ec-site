package com.ecsite.module.catalog.event;

import com.ecsite.common.model.base.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * 商品削除。在庫モジュールが、商品のすべての在庫と調整履歴を消す。
 *
 * @param eventId    イベント ID
 * @param occurredAt 発生時刻
 * @param productId  商品 ID
 */
public record ProductDeleted(UUID eventId, Instant occurredAt, UUID productId) implements DomainEvent {

    /**
     * イベントを作る。
     *
     * @param occurredAt 発生時刻
     * @param productId  商品 ID
     * @return イベント
     */
    public static ProductDeleted of(Instant occurredAt, UUID productId) {
        return new ProductDeleted(UUID.randomUUID(), occurredAt, productId);
    }
}

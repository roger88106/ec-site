package com.ecsite.module.catalog.event;

import com.ecsite.common.model.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * 商品非公開（将来の検索インデックス同期用）
 *
 * @param eventId    イベント ID
 * @param occurredAt 発生時刻
 * @param productId  商品 ID
 */
public record ProductUnpublished(UUID eventId, Instant occurredAt, UUID productId) implements DomainEvent {

    /**
     * イベントを作る。
     *
     * @param occurredAt 発生時刻
     * @param productId  商品 ID
     * @return イベント
     */
    public static ProductUnpublished of(Instant occurredAt, UUID productId) {
        return new ProductUnpublished(UUID.randomUUID(), occurredAt, productId);
    }
}

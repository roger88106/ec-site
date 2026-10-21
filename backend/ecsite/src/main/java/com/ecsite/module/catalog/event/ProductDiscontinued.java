package com.ecsite.module.catalog.event;

import com.ecsite.common.model.base.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * 商品販売終了（将来の検索インデックス同期用）
 *
 * @param eventId    イベント ID
 * @param occurredAt 発生時刻
 * @param productId  商品 ID
 */
public record ProductDiscontinued(UUID eventId, Instant occurredAt, UUID productId) implements DomainEvent {

    /**
     * イベントを作る。
     *
     * @param occurredAt 発生時刻
     * @param productId  商品 ID
     * @return イベント
     */
    public static ProductDiscontinued of(Instant occurredAt, UUID productId) {
        return new ProductDiscontinued(UUID.randomUUID(), occurredAt, productId);
    }
}

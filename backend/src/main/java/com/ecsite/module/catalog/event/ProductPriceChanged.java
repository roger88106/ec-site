package com.ecsite.module.catalog.event;

import com.ecsite.common.model.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * 商品価格変更（将来の検索インデックス同期用）
 *
 * @param eventId    イベント ID
 * @param occurredAt 発生時刻
 * @param productId  商品 ID
 * @param priceExclTax 変更後の税抜価格（円）
 */
public record ProductPriceChanged(UUID eventId, Instant occurredAt, UUID productId, long priceExclTax) implements DomainEvent {

    /**
     * イベントを作る。
     *
     * @param occurredAt 発生時刻
     * @param productId  商品 ID
     * @param priceExclTax 変更後の税抜価格（円）
     * @return イベント
     */
    public static ProductPriceChanged of(Instant occurredAt, UUID productId, long priceExclTax) {
        return new ProductPriceChanged(UUID.randomUUID(), occurredAt, productId, priceExclTax);
    }
}

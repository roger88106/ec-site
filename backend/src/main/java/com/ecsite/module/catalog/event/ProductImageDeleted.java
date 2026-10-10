package com.ecsite.module.catalog.event;

import com.ecsite.common.model.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * 商品画像削除。商品カタログ自身が、配信のバケットのファイルを消す（docs/modules/catalog.md）。
 *
 * @param eventId    イベント ID
 * @param occurredAt 発生時刻
 * @param productId  商品 ID
 * @param imageId    画像 ID
 * @param storageKey 配信のバケットのキー
 */
public record ProductImageDeleted(UUID eventId, Instant occurredAt, UUID productId, UUID imageId, String storageKey)
        implements DomainEvent {

    /**
     * イベントを作る。
     *
     * @param occurredAt 発生時刻
     * @param productId  商品 ID
     * @param imageId    画像 ID
     * @param storageKey 配信のバケットのキー
     * @return イベント
     */
    public static ProductImageDeleted of(Instant occurredAt, UUID productId, UUID imageId, String storageKey) {
        return new ProductImageDeleted(UUID.randomUUID(), occurredAt, productId, imageId, storageKey);
    }
}

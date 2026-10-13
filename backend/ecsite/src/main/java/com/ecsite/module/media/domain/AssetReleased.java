package com.ecsite.module.media.domain;

import com.ecsite.common.model.base.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * アセット解放。メディアモジュールの中だけで使い、コミットの後に配信のファイルを消す（docs/backend/modules/media.md）。
 *
 * @param eventId    イベント ID
 * @param occurredAt 発生時刻
 * @param assetId    アセット ID
 * @param storageKey 配信のバケットのキー
 */
public record AssetReleased(UUID eventId, Instant occurredAt, UUID assetId, String storageKey) implements DomainEvent {

    /**
     * イベントを作る。
     *
     * @param occurredAt 発生時刻
     * @param asset      手放したアセット
     * @return イベント
     */
    public static AssetReleased of(Instant occurredAt, Asset asset) {
        return new AssetReleased(UUID.randomUUID(), occurredAt, asset.id(), asset.storageKey());
    }
}

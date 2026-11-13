package com.ecsite.module.media.application;

import com.ecsite.common.model.base.DomainEvent;
import com.ecsite.module.media.domain.Asset;

import java.time.Instant;
import java.util.UUID;

/**
 * アセット解放
 *
 * <p>アセットの記録の削除のコミットの後に、配信のファイルを削除するための、メディアモジュールの中だけで使うイベント。
 * ロールバックしたときは、記録が戻るのでファイルを残す。記録の削除と同じトランザクションで記録されるので、
 * ファイルの削除が失敗しても再発行でやり直せる。
 *
 * @param eventId    イベント ID
 * @param occurredAt 発生時刻
 * @param assetId    アセット ID
 * @param storageKey 配信のバケットのキー
 */
record AssetReleased(UUID eventId, Instant occurredAt, UUID assetId, String storageKey) implements DomainEvent {

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

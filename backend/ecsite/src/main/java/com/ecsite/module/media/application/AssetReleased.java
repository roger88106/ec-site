package com.ecsite.module.media.application;

import com.ecsite.common.model.base.DomainEvent;
import com.ecsite.common.model.id.AssetId;
import com.ecsite.module.media.domain.Asset;
import com.ecsite.module.media.domain.PendingAsset;

import java.time.Instant;
import java.util.UUID;

/**
 * アセット解放
 *
 * <p>アセットか仮のアセットの記録の削除のコミットの後に、配信のファイルを削除するための、メディアモジュールの中だけで使うイベント。
 * ロールバックしたときは、記録が戻るのでファイルを残す。記録の削除と同じトランザクションで記録されるので、
 * ファイルの削除が失敗しても再発行でやり直せる。
 *
 * @param eventId    イベント ID
 * @param occurredAt 発生時刻
 * @param assetId    アセット ID
 * @param storageKey 配信のバケットのキー
 */
record AssetReleased(UUID eventId, Instant occurredAt, AssetId assetId, String storageKey) implements DomainEvent {

    /**
     * イベントを作る。
     *
     * @param occurredAt 発生時刻
     * @param asset      手放したアセット
     * @return イベント
     */
    public static AssetReleased of(Instant occurredAt, Asset asset) {
        return new AssetReleased(DomainEvent.newEventId(), occurredAt, asset.id(), asset.storageKey());
    }

    /**
     * 削除した仮のアセットから、イベントを作る。
     *
     * @param occurredAt 発生時刻
     * @param pending    削除した仮のアセット
     * @return イベント
     */
    public static AssetReleased of(Instant occurredAt, PendingAsset pending) {
        return new AssetReleased(DomainEvent.newEventId(), occurredAt, pending.id(), pending.storageKey());
    }
}

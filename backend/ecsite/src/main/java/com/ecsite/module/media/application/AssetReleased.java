package com.ecsite.module.media.application;

import com.ecsite.common.model.base.DomainEvent;
import com.ecsite.module.media.domain.Asset;

import java.time.Instant;
import java.util.UUID;

/**
 * アセット解放
 *
 * <p>メディアモジュールの中だけで使うイベント。
 *
 * <p>※モジュールの中でも直接呼ばずにイベントにするのは、配信のファイルの削除を、アセットの記録を消すトランザクションの
 * コミットの後に回すため。ロールバックしたときはファイルを残す（記録が戻るため）。イベントはアセットの記録の削除と
 * 同じトランザクションで記録されるので、コミットの後に削除が失敗しても、再発行でやり直せる。
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

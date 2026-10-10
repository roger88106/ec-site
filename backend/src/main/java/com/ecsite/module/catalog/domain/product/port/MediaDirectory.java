package com.ecsite.module.catalog.domain.product.port;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * メディアの腐敗防止層（constitution 4.1）。<br />
 * メディアモジュールの API を、商品カタログが必要とする形で包む。受付の範囲は商品 ID にする（別の商品の受付を確定できない）。
 */
public interface MediaDirectory {

    /**
     * 商品の画像のアップロードを受け付ける（AC-39）。形式と大きさはメディアモジュールが確かめる。
     *
     * @param productId     商品 ID
     * @param contentType   ファイルの形式（申告）
     * @param contentLength ファイルの大きさ（申告）
     * @return 受付
     */
    ImageUpload requestImageUpload(UUID productId, String contentType, long contentLength);

    /**
     * 受け付けた画像を確定する（AC-37、39）。呼んだ側のトランザクションの中で呼ぶ。
     *
     * @param productId 商品 ID
     * @param uploadId  受付 ID
     * @return アセット ID
     */
    UUID confirmImage(UUID productId, UUID uploadId);

    /**
     * アセットを手放す。配信のファイルは、コミットの後にメディアモジュールが消す。
     *
     * @param assetIds アセット ID
     */
    void release(Collection<UUID> assetIds);

    /**
     * アセットの配信の URL をまとめて返す。
     *
     * @param assetIds アセット ID
     * @return アセット ID → 配信の URL
     */
    Map<UUID, URI> urls(Collection<UUID> assetIds);

    /**
     * 画像のアップロードの受付
     *
     * @param uploadId  受付 ID
     * @param uploadUrl ファイルを PUT する署名付き URL
     * @param headers   PUT に付けるヘッダー
     * @param expiresAt 署名付き URL の期限
     */
    record ImageUpload(UUID uploadId, URI uploadUrl, Map<String, String> headers, Instant expiresAt) {
    }
}

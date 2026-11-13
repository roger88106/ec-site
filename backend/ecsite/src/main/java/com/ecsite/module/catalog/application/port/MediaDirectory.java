package com.ecsite.module.catalog.application.port;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.catalog.application.model.PreparedImage;
import com.ecsite.module.catalog.application.model.view.ImageUploadView;

import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * メディアの腐敗防止層の port
 *
 * <p>メディアモジュールの API を、商品カタログが必要とする形で包む。受付の範囲は商品 ID にする（別の商品の受付を用意できない）。
 */
public interface MediaDirectory {

    /**
     * 商品の画像のアップロードを受け付ける。
     *
     * @param productId     商品 ID
     * @param contentType   ファイルの形式（申告）
     * @param contentLength ファイルの大きさ（申告）
     * @return 受付
     * @throws BusinessException 形式が対象外
     * @throws BusinessException 大きさが範囲の外
     */
    ImageUploadView requestImageUpload(UUID productId, String contentType, long contentLength);

    /**
     * 受け付けた画像を用意する。
     *
     * <p>中身を確認し、変換して配信の置き場所へ置く。アセットは記録しない。
     *
     * @param productId 商品 ID
     * @param uploadId  受付 ID
     * @return 用意した画像
     * @throws BusinessException 受付がない（別の商品の受付を含む）
     * @throws BusinessException 大きさが範囲の外
     * @throws BusinessException 中身が対象の画像でない
     * @throws BusinessException 変換できない
     */
    PreparedImage prepareImage(UUID productId, UUID uploadId);

    /**
     * 用意した画像をアセットとして記録する。
     *
     * <p>呼んだ側のトランザクションに加わる。
     *
     * @param prepared 用意した画像
     * @return アセット ID
     */
    UUID registerImage(PreparedImage prepared);

    /**
     * 用意した画像の配信のファイルを消す。
     *
     * <p>消すのに失敗しても例外を投げない。
     *
     * @param prepared 用意した画像
     */
    void discardImage(PreparedImage prepared);

    /**
     * アセットを手放す。
     *
     * <p>配信のファイルは、コミットの後に消える。
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
}

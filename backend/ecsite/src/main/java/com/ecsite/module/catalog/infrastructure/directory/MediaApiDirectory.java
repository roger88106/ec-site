package com.ecsite.module.catalog.infrastructure.directory;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.catalog.application.model.PreparedImage;
import com.ecsite.module.catalog.application.model.view.ImageUploadView;
import com.ecsite.module.catalog.application.port.MediaDirectory;
import com.ecsite.module.media.api.MediaApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * メディアの腐敗防止層
 *
 * <p>メディアモジュールの API を呼ぶ。受付の範囲は商品ごとにする。
 */
@Component
@RequiredArgsConstructor
class MediaApiDirectory implements MediaDirectory {

    /**
     * メディアモジュールの API
     */
    private final MediaApi mediaApi;

    /**
     * 商品の画像のアップロードの受付を、メディアモジュールに依頼する。
     *
     * @param productId     商品 ID
     * @param contentType   ファイルの形式
     * @param contentLength ファイルの大きさ（バイト）
     * @return 受付の情報（アップロードの URL など）
     * @throws BusinessException 形式が対象外、または大きさが範囲の外
     */
    @Override
    public ImageUploadView requestImageUpload(UUID productId, String contentType, long contentLength) {
        var upload = mediaApi.requestImageUpload(scope(productId), contentType, contentLength);
        return new ImageUploadView(upload.uploadId(), upload.uploadUrl(), upload.headers(), upload.expiresAt());
    }

    /**
     * 受け付けた画像を、メディアモジュールで用意する。
     *
     * @param productId 商品 ID
     * @param uploadId  受付の ID
     * @return 用意した画像
     * @throws BusinessException 受付がない（別の商品の受付を含む）、大きさが範囲の外、中身が対象の画像でない、変換できない
     */
    @Override
    public PreparedImage prepareImage(UUID productId, UUID uploadId) {
        return new PreparedImage(mediaApi.prepareImage(scope(productId), uploadId).assetId());
    }

    /**
     * 用意した画像を、メディアモジュールで登録する。
     *
     * @param prepared 用意した画像
     * @return アセット ID
     */
    @Override
    public UUID registerImage(PreparedImage prepared) {
        return mediaApi.registerImage(toMedia(prepared));
    }

    /**
     * 用意した画像を、メディアモジュールで破棄する。
     *
     * @param prepared 用意した画像
     */
    @Override
    public void discardImage(PreparedImage prepared) {
        mediaApi.discardImage(toMedia(prepared));
    }

    /**
     * アセットを手放す。
     *
     * <p>配信のファイルは、コミットの後に消える。
     *
     * @param assetIds アセット ID
     */
    @Override
    public void release(Collection<UUID> assetIds) {
        mediaApi.release(assetIds);
    }

    /**
     * アセットの配信の URL をまとめて返す。
     *
     * @param assetIds アセット ID
     * @return アセット ID → URL
     */
    @Override
    public Map<UUID, URI> urls(Collection<UUID> assetIds) {
        return mediaApi.urls(assetIds);
    }

// ============================== プライベートメソッド ==============================

    /**
     * 受付の範囲を返す。
     *
     * <p>商品 ID にする。
     */
    private static String scope(UUID productId) {
        return productId.toString();
    }

    /**
     * 用意した画像を、メディアモジュールの型に変換する。
     */
    private static com.ecsite.module.media.api.model.PreparedImage toMedia(PreparedImage prepared) {
        return new com.ecsite.module.media.api.model.PreparedImage(prepared.assetId());
    }
}

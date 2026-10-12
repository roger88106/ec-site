package com.ecsite.module.catalog.infrastructure;

import com.ecsite.module.catalog.domain.product.port.MediaDirectory;
import com.ecsite.module.media.api.MediaApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * メディアの腐敗防止層の実装。メディアモジュールの API（{@link MediaApi}）を呼ぶ。
 */
@Component
@RequiredArgsConstructor
class MediaApiDirectory implements MediaDirectory {

    /**
     * メディアモジュールの API
     */
    private final MediaApi mediaApi;

    @Override
    public ImageUpload requestImageUpload(UUID productId, String contentType, long contentLength) {
        var upload = mediaApi.requestImageUpload(scope(productId), contentType, contentLength);
        return new ImageUpload(upload.uploadId(), upload.uploadUrl(), upload.headers(), upload.expiresAt());
    }

    @Override
    public UUID confirmImage(UUID productId, UUID uploadId) {
        return mediaApi.confirmImage(scope(productId), uploadId);
    }

    @Override
    public void release(Collection<UUID> assetIds) {
        mediaApi.release(assetIds);
    }

    @Override
    public Map<UUID, URI> urls(Collection<UUID> assetIds) {
        return mediaApi.urls(assetIds);
    }

// ============================== プライベートメソッド ==============================

    /**
     * 受付の範囲。商品 ID にする
     */
    private static String scope(UUID productId) {
        return productId.toString();
    }
}

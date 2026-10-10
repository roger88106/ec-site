package com.ecsite.module.catalog.application;

import com.ecsite.module.catalog.domain.product.vo.ImageFormat;

import java.util.UUID;

/**
 * 画像のファイルのキーの決まり（plan 001 第 7.1 節）。キーの形はここでだけ決める。
 */
final class ProductImageKeys {

    private ProductImageKeys() {
    }

    /**
     * 受付のバケットのキー。商品 ID を含め、別の商品の受付を使えないようにする
     *
     * @param productId 商品 ID
     * @param uploadId  受付 ID
     * @return {productId}/{uploadId}
     */
    static String upload(UUID productId, UUID uploadId) {
        return productId + "/" + uploadId;
    }

    /**
     * 配信のバケットのキー。画像 ID で作り、上書きしない
     *
     * @param productId 商品 ID
     * @param imageId   画像 ID
     * @param format    画像の形式
     * @return products/{productId}/{imageId}.{拡張子}
     */
    static String published(UUID productId, UUID imageId, ImageFormat format) {
        return productPrefix(productId) + imageId + "." + format.extension();
    }

    /**
     * 商品の配信のファイルのキーの接頭辞
     *
     * @param productId 商品 ID
     * @return products/{productId}/
     */
    static String productPrefix(UUID productId) {
        return "products/" + productId + "/";
    }
}

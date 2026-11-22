package com.ecsite.module.catalog.application.model;

import com.ecsite.common.model.id.AssetId;

/**
 * 用意した画像
 *
 * <p>配信の置き場所にあり、アセットはまだ記録していない。
 *
 * @param assetId 登録したときのアセット ID
 */
public record PreparedImage(AssetId assetId) {
}

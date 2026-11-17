package com.ecsite.module.catalog.application.model;

import java.util.UUID;

/**
 * 用意した画像
 *
 * <p>配信の置き場所にあり、アセットはまだ記録していない。
 *
 * @param assetId 登録したときのアセット ID
 */
public record PreparedImage(UUID assetId) {
}

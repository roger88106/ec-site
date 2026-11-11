package com.ecsite.module.media.api.model;

import java.util.UUID;

/**
 * 用意した画像
 *
 * <p>配信のバケットに置いたが、アセットはまだ記録していない。
 *
 * @param assetId 登録したときのアセット ID
 */
public record PreparedImage(UUID assetId) {
}

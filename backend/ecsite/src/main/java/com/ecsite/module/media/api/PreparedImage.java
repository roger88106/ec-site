package com.ecsite.module.media.api;

import java.util.UUID;

/**
 * 用意した画像。配信のバケットに置いたが、アセットはまだ記録していない。<br />
 * 登録（{@link MediaApi#registerImage}）か破棄（{@link MediaApi#discardImage}）に渡す。
 *
 * @param assetId 登録したときのアセット ID
 */
public record PreparedImage(UUID assetId) {
}

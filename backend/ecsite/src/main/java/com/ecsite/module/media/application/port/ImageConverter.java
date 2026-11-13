package com.ecsite.module.media.application.port;

import com.ecsite.common.error.exception.BusinessException;

/**
 * 画像の変換
 *
 * <p>アップロードのバケットのファイルを、配信する形式（WebP）に変換する。
 * 向き（EXIF の Orientation）を画素に反映し、位置情報などのメタデータを除く。
 */
public interface ImageConverter {

    /**
     * アップロードのバケットのファイルを WebP に変換する。
     *
     * @param uploadKey アップロードのバケットのキー
     * @return 変換した WebP
     * @throws BusinessException 壊れたファイルで変換できなかった（{@code IMAGE_CONVERSION_FAILED}）
     * @throws BusinessException 変換の応答が WebP でない（{@code IMAGE_CONVERSION_FAILED}）
     */
    byte[] convertToWebp(String uploadKey);
}

package com.ecsite.module.media.application.port;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.error.exception.SystemException;

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
     * @throws BusinessException 変換できなかった（壊れたファイル、応答が WebP でない）
     * @throws SystemException 変換のサービスに接続できない、時間の上限を超えた
     */
    byte[] convertToWebp(String uploadKey);
}

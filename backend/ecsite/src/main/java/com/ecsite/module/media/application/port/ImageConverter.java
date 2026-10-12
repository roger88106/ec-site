package com.ecsite.module.media.application.port;

/**
 * 画像の変換（plan 001 第 7.5 節）。受付のバケットのファイルを、配信する形式（WebP）に変換する。<br />
 * 向き（EXIF の Orientation）を画素に反映し、位置情報などのメタデータを除く（AC-68、69）。
 * いまは確定の中で同期に呼ぶ。時間がかかるようになったら、イベントの後で呼ぶ形に変えられる。
 */
public interface ImageConverter {

    /**
     * 受付のバケットのファイルを WebP に変換する。
     *
     * @param uploadKey 受付のバケットのキー
     * @return 変換した WebP
     * @throws com.ecsite.common.error.exception.BusinessException 変換できなかった（壊れたファイル、時間切れ。AC-70）
     */
    byte[] convertToWebp(String uploadKey);
}

package com.ecsite.module.media.application.port;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * ファイルの置き場所（オブジェクトストレージ）。<br />
 * アップロードのバケット（非公開。アップロードされたままのファイル）と、配信のバケット（公開）を扱う。
 * AWS（S3）と GCP（GCS）の両方にある機能だけを使えるよう、操作を絞る。
 */
public interface ObjectStorage {

    /**
     * アップロードのバケットへファイルを PUT する署名付き URL を発行する。
     *
     * @param uploadKey     アップロードのバケットのキー
     * @param contentType   ファイルの形式。PUT のときに同じ Content-Type を付ける
     * @param contentLength ファイルの大きさ（バイト）
     * @return 署名付き URL と、PUT に付けるヘッダー、期限
     */
    PresignedUpload presignUpload(String uploadKey, String contentType, long contentLength);

    /**
     * アップロードのバケットのファイルの大きさを返す。
     *
     * @param uploadKey アップロードのバケットのキー
     * @return ファイルの大きさ（バイト）。ファイルがなければ空
     */
    Optional<Long> findUploadSize(String uploadKey);

    /**
     * アップロードのバケットのファイルの先頭を読む（形式の判定に使う）。
     *
     * @param uploadKey アップロードのバケットのキー
     * @param length    読む長さ（バイト）
     * @return 先頭のバイト。ファイルが length より短ければ、ファイルの全体
     */
    byte[] readUploadHead(String uploadKey, int length);

    /**
     * 配信のバケットへファイルを書き込む。長くキャッシュさせる（キーを上書きしないため）。
     *
     * @param publicKey   配信のバケットのキー
     * @param content     ファイルの中身（変換した画像）
     * @param contentType 配信するときの形式
     */
    void putPublished(String publicKey, byte[] content, String contentType);

    /**
     * 配信のバケットのファイルを消す。ファイルがなくても成功とする（何度行っても同じ結果）。
     *
     * @param publicKey 配信のバケットのキー
     */
    void deletePublished(String publicKey);

    /**
     * 配信のバケットのファイルの URL を返す。
     *
     * @param publicKey 配信のバケットのキー
     * @return 配信の URL
     */
    URI publicUrl(String publicKey);

    /**
     * 署名付きのアップロード
     *
     * @param url       PUT する URL
     * @param headers   PUT に付けるヘッダー
     * @param expiresAt URL の期限
     */
    record PresignedUpload(URI url, Map<String, String> headers, Instant expiresAt) {
    }
}

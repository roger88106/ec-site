package com.ecsite.module.media.api.model;

import com.ecsite.common.model.id.UploadId;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

/**
 * 画像のアップロードの受付
 *
 * @param uploadId  受付 ID（用意のときに渡す）
 * @param uploadUrl ファイルを PUT する署名付き URL
 * @param headers   PUT に付けるヘッダー
 * @param expiresAt 署名付き URL の期限
 */
public record ImageUpload(UploadId uploadId, URI uploadUrl, Map<String, String> headers, Instant expiresAt) {
}

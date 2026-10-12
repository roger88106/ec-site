package com.ecsite.module.media.api;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 画像のアップロードの受付
 *
 * @param uploadId  受付 ID（確定のときに渡す）
 * @param uploadUrl ファイルを PUT する署名付き URL
 * @param headers   PUT に付けるヘッダー
 * @param expiresAt 署名付き URL の期限
 */
public record ImageUpload(UUID uploadId, URI uploadUrl, Map<String, String> headers, Instant expiresAt) {
}

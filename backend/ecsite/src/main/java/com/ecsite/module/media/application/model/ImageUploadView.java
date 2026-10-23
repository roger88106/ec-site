package com.ecsite.module.media.application.model;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 画像のアップロードの受付の表示
 *
 * @param uploadId  受付 ID（用意のときに渡す）
 * @param uploadUrl ファイルを PUT する署名付き URL
 * @param headers   PUT に付けるヘッダー
 * @param expiresAt 署名付き URL の期限
 */
public record ImageUploadView(UUID uploadId, URI uploadUrl, Map<String, String> headers, Instant expiresAt) {
}

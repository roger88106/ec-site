package com.ecsite.module.catalog.application.model.view;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 画像のアップロードの受付
 *
 * @param uploadId  受付 ID（登録のときに送る）
 * @param uploadUrl ファイルを PUT する署名付き URL
 * @param headers   PUT に付けるヘッダー
 * @param expiresAt 署名付き URL の期限
 */
public record ImageUploadView(UUID uploadId, URI uploadUrl, Map<String, String> headers, Instant expiresAt) {
}

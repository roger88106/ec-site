package com.ecsite.module.catalog.application.model.view;

import com.ecsite.common.model.id.UploadId;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

/**
 * 画像のアップロードの受付
 *
 * @param uploadId  受付 ID
 * @param uploadUrl ファイルを PUT する署名付き URL
 * @param headers   PUT に付けるヘッダー
 * @param expiresAt 署名付き URL の期限
 */
public record ImageUploadView(UploadId uploadId, URI uploadUrl, Map<String, String> headers, Instant expiresAt) {
}

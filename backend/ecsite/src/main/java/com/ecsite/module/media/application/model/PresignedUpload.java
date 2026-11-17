package com.ecsite.module.media.application.model;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

/**
 * 署名付きのアップロード
 *
 * @param url       PUT する URL
 * @param headers   PUT に付けるヘッダー
 * @param expiresAt URL の期限
 */
public record PresignedUpload(URI url, Map<String, String> headers, Instant expiresAt) {
}

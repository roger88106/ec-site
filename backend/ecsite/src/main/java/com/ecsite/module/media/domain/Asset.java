package com.ecsite.module.media.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * アセット（docs/backend/modules/media.md「アセット」）。検証して配信のバケットに置いた 1 つのファイル。
 *
 * @param id         アセット ID
 * @param format     形式
 * @param storageKey 配信のバケットのキー
 * @param createdAt  確定した時刻
 */
public record Asset(UUID id, ImageFormat format, String storageKey, Instant createdAt) {
}

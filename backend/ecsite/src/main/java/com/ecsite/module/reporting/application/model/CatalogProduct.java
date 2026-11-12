package com.ecsite.module.reporting.application.model;

import java.util.List;
import java.util.UUID;

/**
 * 商品カタログの商品
 *
 * @param productId 商品 ID
 * @param name      商品名
 * @param variants  バリアント（商品カタログの並びの順）
 */
public record CatalogProduct(UUID productId, String name, List<CatalogVariant> variants) {
}

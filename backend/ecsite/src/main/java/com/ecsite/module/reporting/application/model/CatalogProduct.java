package com.ecsite.module.reporting.application.model;

import com.ecsite.common.model.id.ProductId;

import java.util.List;

/**
 * 商品カタログの商品
 *
 * @param productId 商品 ID
 * @param name      商品名
 * @param variants  バリアント（商品カタログの並びの順）
 */
public record CatalogProduct(ProductId productId, String name, List<CatalogVariant> variants) {
}

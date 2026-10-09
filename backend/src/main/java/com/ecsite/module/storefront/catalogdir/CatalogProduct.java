package com.ecsite.module.storefront.catalogdir;

import java.util.List;
import java.util.UUID;

/**
 * 商品カタログから受け取った、一覧の 1 件の商品
 *
 * @param id                 商品 ID
 * @param name               商品名
 * @param priceInclTax       税込価格（円）
 * @param priceExclTax       税抜価格（円）
 * @param sellableVariantIds 販売中（販売停止でない）のバリアントの ID
 */
public record CatalogProduct(UUID id, String name, long priceInclTax, long priceExclTax, List<UUID> sellableVariantIds) {
}

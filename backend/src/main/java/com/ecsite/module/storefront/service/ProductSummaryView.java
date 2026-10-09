package com.ecsite.module.storefront.service;

import java.util.UUID;

/**
 * C 側の商品一覧の 1 件（AC-55、56）
 *
 * @param id           商品 ID
 * @param name         商品名
 * @param priceInclTax 税込価格（円）
 * @param priceExclTax 税抜価格（円）
 * @param inStock      販売中のいずれかのバリアントに在庫がある
 */
public record ProductSummaryView(UUID id, String name, long priceInclTax, long priceExclTax, boolean inStock) {
}

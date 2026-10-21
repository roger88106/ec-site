package com.ecsite.module.storefront.application;

import com.ecsite.common.model.value.Money;
import jakarta.annotation.Nullable;

import java.util.UUID;

/**
 * C 側の商品一覧の 1 件
 *
 * @param id           商品 ID
 * @param name         商品名
 * @param priceInclTax 税込価格（円）
 * @param priceExclTax 税抜価格（円）
 * @param inStock      販売中のいずれかのバリアントに在庫がある
 * @param imageUrl     代表画像の URL。なければ null
 */
public record ProductSummaryView(UUID id, String name, Money priceInclTax, Money priceExclTax, boolean inStock,
                                 @Nullable String imageUrl) {
}

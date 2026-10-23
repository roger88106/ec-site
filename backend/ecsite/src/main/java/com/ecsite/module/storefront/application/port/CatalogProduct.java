package com.ecsite.module.storefront.application.port;

import com.ecsite.common.model.value.Money;
import org.jspecify.annotations.Nullable;

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
 * @param imageUrl           代表画像の URL。なければ null
 */
public record CatalogProduct(UUID id, String name, Money priceInclTax, Money priceExclTax, List<UUID> sellableVariantIds,
                             @Nullable String imageUrl) {
}

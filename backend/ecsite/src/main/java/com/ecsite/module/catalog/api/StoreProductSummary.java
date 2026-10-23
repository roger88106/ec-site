package com.ecsite.module.catalog.api;

import com.ecsite.common.model.value.Money;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * C 側の商品一覧の 1 件。価格は表示時刻の税率で計算した税込価格と、税抜価格。
 *
 * @param id                 商品 ID
 * @param name               商品名
 * @param priceInclTax       税込価格（円）
 * @param priceExclTax       税抜価格（円）
 * @param sellableVariantIds 販売中（販売停止でない）のバリアントの ID。在庫の有無の判定に使う
 * @param imageUrl           代表画像の URL。画像がなければ null
 */
public record StoreProductSummary(UUID id, String name, Money priceInclTax, Money priceExclTax,
                                  List<UUID> sellableVariantIds, @Nullable String imageUrl) {
}

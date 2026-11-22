package com.ecsite.module.catalog.api.model;

import com.ecsite.common.model.id.ProductId;
import com.ecsite.common.model.id.VariantId;
import com.ecsite.common.model.value.Money;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 店舗に表示される商品の一覧の 1 件
 *
 * <p>価格は表示時刻の税率で計算した税込価格と、税抜価格。
 *
 * @param id                 商品 ID
 * @param name               商品名
 * @param priceInclTax       税込価格（円）
 * @param priceExclTax       税抜価格（円）
 * @param sellableVariantIds 販売中（販売停止でない）のバリアントの ID
 * @param imageUrl           代表画像の URL。画像がなければ null
 */
public record ShownProductSummary(ProductId id, String name, Money priceInclTax, Money priceExclTax,
                                  List<VariantId> sellableVariantIds, @Nullable String imageUrl) {
}

package com.ecsite.module.catalog.application.model.command;

import com.ecsite.common.model.value.Money;
import lombok.NonNull;

import java.util.List;
import java.util.UUID;

/**
 * 商品の編集コマンド
 *
 * @param productId     商品 ID
 * @param name          商品名
 * @param description   説明
 * @param taxCategoryId 税区分 ID
 * @param priceExclTax  税抜価格（円）
 * @param categoryIds   所属カテゴリの ID
 * @param version       取得時のバージョン
 */
public record UpdateProductCommand(@NonNull UUID productId, @NonNull String name, @NonNull String description,
                                   @NonNull UUID taxCategoryId, @NonNull Money priceExclTax,
                                   @NonNull List<UUID> categoryIds, long version) {
}

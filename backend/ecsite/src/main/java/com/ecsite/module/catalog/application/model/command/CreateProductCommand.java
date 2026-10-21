package com.ecsite.module.catalog.application.model.command;

import com.ecsite.common.model.value.Money;

import java.util.List;
import java.util.UUID;

/**
 * 商品の登録コマンド
 *
 * @param productCode   商品品番
 * @param name          商品名
 * @param description   説明
 * @param taxCategoryId 税区分 ID
 * @param priceExclTax  税抜価格（円）
 * @param categoryIds   所属カテゴリの ID
 * @param axes          バリアント軸の名前（並び順）
 */
public record CreateProductCommand(String productCode, String name, String description, UUID taxCategoryId,
                                   Money priceExclTax, List<UUID> categoryIds, List<String> axes) {
}

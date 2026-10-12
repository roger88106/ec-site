package com.ecsite.module.catalog.application.model.view;

import java.util.UUID;

/**
 * B 側の商品一覧の 1 件のビュー。在庫数の合計は在庫モジュールの API で取得する。
 *
 * @param id           商品 ID
 * @param productCode  商品品番
 * @param name         商品名
 * @param status       公開状態
 * @param variantCount バリアント数
 */
public record ProductSummaryView(UUID id, String productCode, String name, String status, int variantCount) {
}

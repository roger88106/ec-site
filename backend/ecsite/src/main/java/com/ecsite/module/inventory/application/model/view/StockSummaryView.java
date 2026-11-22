package com.ecsite.module.inventory.application.model.view;

import com.ecsite.common.model.id.ProductId;

/**
 * 商品ごとの在庫数の合計のビュー
 *
 * @param productId     商品 ID
 * @param totalQuantity 在庫数の合計。在庫がまだない商品は 0
 */
public record StockSummaryView(ProductId productId, long totalQuantity) {
}

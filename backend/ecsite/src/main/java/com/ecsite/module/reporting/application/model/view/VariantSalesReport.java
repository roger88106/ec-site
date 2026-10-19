package com.ecsite.module.reporting.application.model.view;

import java.util.List;
import java.util.UUID;

/**
 * バリエーションの売れ行き
 *
 * @param productId        商品 ID
 * @param productName      商品名
 * @param variants         バリアントごとの売れ行き（販売数量の多い順、同じなら商品カタログの並びの順）
 * @param shippingExcluded 送料を除いたか（バリエーションの売れ行きは、いつも除く）
 */
public record VariantSalesReport(UUID productId, String productName, List<VariantSales> variants, boolean shippingExcluded) {
}

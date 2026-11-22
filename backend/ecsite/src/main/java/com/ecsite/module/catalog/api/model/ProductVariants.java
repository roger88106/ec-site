package com.ecsite.module.catalog.api.model;

import com.ecsite.common.model.id.ProductId;
import com.ecsite.common.model.id.VariantId;

import java.util.List;

/**
 * 商品とそのバリアントの一覧
 *
 * @param productId 商品 ID
 * @param name      商品名
 * @param variants  バリアント（商品カタログの並びの順）
 */
public record ProductVariants(ProductId productId, String name, List<Variant> variants) {

    /**
     * バリアント
     *
     * @param variantId   バリアント ID
     * @param variantCode バリアント品番
     * @param axisValues  軸の名前と値
     */
    public record Variant(VariantId variantId, String variantCode, List<AxisValue> axisValues) {
    }
}

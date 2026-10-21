package com.ecsite.module.catalog.api;

import java.util.List;
import java.util.UUID;

/**
 * レポートに公開する、商品とそのバリアントの一覧。
 *
 * @param productId 商品 ID
 * @param name      商品名
 * @param variants  バリアント（商品カタログの並びの順）
 */
public record ReportProduct(UUID productId, String name, List<Variant> variants) {

    /**
     * バリアント
     *
     * @param variantId   バリアント ID
     * @param variantCode バリアント品番
     * @param axisValues  軸の名前と値
     */
    public record Variant(UUID variantId, String variantCode, List<AxisValue> axisValues) {
    }

    /**
     * 軸の名前と値
     *
     * @param name  軸の名前（例：色）
     * @param value 軸の値（例：白）
     */
    public record AxisValue(String name, String value) {
    }
}

package com.ecsite.module.storefront.service;

import java.util.List;
import java.util.UUID;

/**
 * C 側の商品の詳細（AC-62〜64）
 *
 * @param id           商品 ID
 * @param name         商品名
 * @param description  説明
 * @param priceInclTax 税込価格（円）
 * @param priceExclTax 税抜価格（円）
 * @param axes         バリアント軸と、その値のすべて
 * @param variants     すべてのバリアント（表示順）
 */
public record ProductDetailView(UUID id, String name, String description, long priceInclTax, long priceExclTax,
                                List<AxisView> axes, List<VariantView> variants) {

    /**
     * バリアント軸と、その値のすべて（選べない値も含む）
     *
     * @param name   軸の名前
     * @param values 値
     */
    public record AxisView(String name, List<String> values) {
    }

    /**
     * バリアント。在庫は有無だけ（AC-64）
     *
     * @param id             バリアント ID
     * @param axisValues     軸の値
     * @param inStock        在庫がある
     * @param salesSuspended 販売停止中
     */
    public record VariantView(UUID id, List<String> axisValues, boolean inStock, boolean salesSuspended) {
    }
}

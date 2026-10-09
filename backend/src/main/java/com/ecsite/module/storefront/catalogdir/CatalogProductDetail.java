package com.ecsite.module.storefront.catalogdir;

import java.util.List;
import java.util.UUID;

/**
 * 商品カタログから受け取った、商品の詳細
 *
 * @param id           商品 ID
 * @param name         商品名
 * @param description  説明
 * @param priceInclTax 税込価格（円）
 * @param priceExclTax 税抜価格（円）
 * @param axes         バリアント軸の名前（並び順）
 * @param variants     すべてのバリアント（表示順）
 */
public record CatalogProductDetail(UUID id, String name, String description, long priceInclTax, long priceExclTax,
                                   List<String> axes, List<Variant> variants) {

    /**
     * バリアント
     *
     * @param id             バリアント ID
     * @param axisValues     軸の値（axes と同じ並び）
     * @param salesSuspended 販売停止中
     */
    public record Variant(UUID id, List<String> axisValues, boolean salesSuspended) {
    }
}

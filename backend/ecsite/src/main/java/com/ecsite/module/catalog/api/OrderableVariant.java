package com.ecsite.module.catalog.api;

import com.ecsite.common.model.value.Money;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 注文に使うバリアントの情報（docs/modules/catalog.md「他モジュールへの公開」）。<br />
 * カートに入れられるか、購入できるかの判定と、注文の写しに使う。
 *
 * @param variantId      バリアント ID
 * @param productId      商品 ID
 * @param productCode    商品品番
 * @param variantCode    バリアント品番
 * @param productName    商品名
 * @param axisValues     軸の名前と値（商品の軸の並び）。軸のない商品は空
 * @param priceExclTax   税抜価格（商品の価格。バリアント間で共通）
 * @param taxCategoryId  税区分 ID
 * @param imageUrl       バリアントの画像、なければ商品の代表画像の URL。どちらもなければ null
 * @param visible        商品が現在 C 側に表示されるか
 * @param salesSuspended バリアントが販売停止か
 */
public record OrderableVariant(UUID variantId, UUID productId, String productCode, String variantCode,
                               String productName, List<AxisValue> axisValues, Money priceExclTax,
                               UUID taxCategoryId, @Nullable String imageUrl, boolean visible,
                               boolean salesSuspended) {

    /**
     * 軸の名前と値
     *
     * @param name  軸の名前（例：色）
     * @param value 軸の値（例：白）
     */
    public record AxisValue(String name, String value) {
    }
}

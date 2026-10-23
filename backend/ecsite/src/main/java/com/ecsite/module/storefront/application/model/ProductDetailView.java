package com.ecsite.module.storefront.application.model;

import com.ecsite.common.model.value.Money;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * C 側の商品の詳細
 *
 * @param id           商品 ID
 * @param name         商品名
 * @param description  説明
 * @param priceInclTax 税込価格（円）
 * @param priceExclTax 税抜価格（円）
 * @param axes         バリアント軸と、その値のすべて
 * @param variants     すべてのバリアント（表示順）
 * @param imageUrls    画像の URL（表示順。先頭が代表画像）
 */
public record ProductDetailView(UUID id, String name, String description, Money priceInclTax, Money priceExclTax,
                                List<AxisView> axes, List<VariantView> variants, List<String> imageUrls) {

    /**
     * バリアント軸と、その値のすべて（選べない値も含む）
     *
     * @param name   軸の名前
     * @param values 値
     */
    public record AxisView(String name, List<String> values) {
    }

    /**
     * バリアント。在庫は有無だけ
     *
     * @param id             バリアント ID
     * @param axisValues     軸の値
     * @param inStock        在庫がある
     * @param salesSuspended 販売停止中
     * @param imageUrl       指定した画像の URL。なければ null（代表画像を使う）
     */
    public record VariantView(UUID id, List<String> axisValues, boolean inStock, boolean salesSuspended,
                              @Nullable String imageUrl) {
    }
}

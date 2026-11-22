package com.ecsite.module.catalog.api.model;

import com.ecsite.common.model.id.ProductId;
import com.ecsite.common.model.id.VariantId;
import com.ecsite.common.model.value.Money;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 店舗に表示される商品の詳細
 *
 * @param id           商品 ID
 * @param name         商品名
 * @param description  説明
 * @param priceInclTax 税込価格（円）
 * @param priceExclTax 税抜価格（円）
 * @param axes         バリアント軸の名前（並び順）
 * @param variants     全てのバリアント（表示順。販売停止中のものも含む）
 * @param imageUrls    画像の URL（表示順。先頭が代表画像）
 */
public record ShownProductDetail(ProductId id, String name, String description, Money priceInclTax, Money priceExclTax,
                                 List<String> axes, List<Variant> variants, List<String> imageUrls) {

    /**
     * バリアント
     *
     * @param id             バリアント ID
     * @param axisValues     軸の値（axes と同じ並び）
     * @param salesSuspended 販売停止中
     * @param imageUrl       指定した画像の URL。なければ null（代表画像を使う）
     */
    public record Variant(VariantId id, List<String> axisValues, boolean salesSuspended, @Nullable String imageUrl) {
    }
}

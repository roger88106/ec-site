package com.ecsite.module.catalog.application.model.view;

import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 商品の詳細のビュー（バリアントを含む）
 *
 * @param id            商品 ID
 * @param productCode   商品品番
 * @param name          商品名
 * @param description   説明
 * @param taxCategoryId 税区分 ID
 * @param priceExclTax  税抜価格（円）
 * @param priceInclTax  現時点の税込価格（円）。有効な税率がなければ null
 * @param categoryIds   所属カテゴリの ID
 * @param axes          バリアント軸の名前（並び順）
 * @param status        公開状態（{@code UNPUBLISHED} / {@code PUBLISHED} / {@code DISCONTINUED}）
 * @param firstShownAt  初回表示日時
 * @param publishStart  公開開始日時
 * @param publishEnd    公開終了日時
 * @param variants      バリアント（表示順）
 * @param version       バージョン
 * @param createdAt     登録日時
 * @param updatedAt     更新日時
 */
public record ProductDetailView(UUID id, String productCode, String name, String description, UUID taxCategoryId,
                                long priceExclTax, @Nullable Long priceInclTax, List<UUID> categoryIds,
                                List<String> axes, String status, @Nullable Instant firstShownAt,
                                @Nullable Instant publishStart, @Nullable Instant publishEnd,
                                List<VariantView> variants, long version, Instant createdAt, Instant updatedAt) {

    /**
     * 税込価格を設定したビューを返す。
     *
     * @param priceInclTax 税込価格（円）。なければ null
     * @return ビュー
     */
    public ProductDetailView withPriceInclTax(@Nullable Long priceInclTax) {
        return new ProductDetailView(id, productCode, name, description, taxCategoryId, priceExclTax, priceInclTax,
                categoryIds, axes, status, firstShownAt, publishStart, publishEnd, variants, version, createdAt, updatedAt);
    }

    /**
     * バリアントのビュー
     *
     * @param id             バリアント ID
     * @param variantCode    バリアント品番
     * @param axisValues     軸の値（商品の軸と同じ並び）
     * @param salesSuspended 販売停止
     */
    public record VariantView(UUID id, String variantCode, List<String> axisValues, boolean salesSuspended) {
    }
}

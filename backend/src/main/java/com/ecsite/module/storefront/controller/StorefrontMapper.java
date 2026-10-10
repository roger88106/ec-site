package com.ecsite.module.storefront.controller;

import com.ecsite.api.store.model.CategoryNode;
import com.ecsite.api.store.model.Price;
import com.ecsite.api.store.model.ProductDetail;
import com.ecsite.api.store.model.ProductSort;
import com.ecsite.api.store.model.ProductSummary;
import com.ecsite.api.store.model.ProductSummaryPage;
import com.ecsite.api.store.model.Variant;
import com.ecsite.api.store.model.VariantAxis;
import com.ecsite.common.paging.PageResult;
import com.ecsite.module.storefront.catalogdir.CatalogCategory;
import com.ecsite.module.storefront.catalogdir.ProductCondition;
import com.ecsite.module.storefront.service.ProductDetailView;
import com.ecsite.module.storefront.service.ProductSummaryView;
import org.springframework.stereotype.Component;

/**
 * 店舗 API のリクエスト・レスポンスと、店舗のサービスのモデルの変換。<br />
 * 画像は US-7 までないので、代表画像・バリアントの画像は null、画像の一覧は空にする。
 */
@Component
class StorefrontMapper {

    /**
     * 並び順を変換する。指定がなければ新着順。
     *
     * @param sort 並び順
     * @return 並び順
     */
    ProductCondition.Sort toSort(ProductSort sort) {
        if (sort == null) {
            return ProductCondition.Sort.NEWEST;
        }
        return switch (sort) {
            case NEWEST -> ProductCondition.Sort.NEWEST;
            case PRICE_ASC -> ProductCondition.Sort.PRICE_ASC;
            case PRICE_DESC -> ProductCondition.Sort.PRICE_DESC;
        };
    }

    /**
     * 商品一覧のレスポンスに変換する。
     *
     * @param page 商品の一覧
     * @return 商品一覧のレスポンス
     */
    ProductSummaryPage toProductSummaryPage(PageResult<ProductSummaryView> page) {
        return new ProductSummaryPage(page.page(), page.size(), page.totalItems(), page.totalPages(),
                page.items().stream().map(StorefrontMapper::toProductSummary).toList());
    }

    /**
     * 商品の詳細のレスポンスに変換する。
     *
     * @param view 商品の詳細
     * @return 商品の詳細のレスポンス
     */
    ProductDetail toProductDetail(ProductDetailView view) {
        return new ProductDetail(
                view.id(),
                view.name(),
                view.description(),
                new Price(view.priceInclTax(), view.priceExclTax()),
                view.imageUrls(),
                view.axes().stream().map(axis -> new VariantAxis(axis.name(), axis.values())).toList(),
                view.variants().stream()
                        .map(variant -> new Variant(variant.id(), variant.axisValues(), variant.inStock(), variant.salesSuspended())
                                .imageUrl(variant.imageUrl()))
                        .toList());
    }

    /**
     * カテゴリの木の節のレスポンスに変換する。
     *
     * @param category カテゴリ
     * @return カテゴリの木の節のレスポンス
     */
    CategoryNode toCategoryNode(CatalogCategory category) {
        return new CategoryNode(category.id(), category.name(), category.children().stream().map(this::toCategoryNode).toList());
    }

// ============================== プライベートメソッド ==============================

    /**
     * 一覧の商品をレスポンスに変換する。代表画像がなければ null。
     */
    private static ProductSummary toProductSummary(ProductSummaryView view) {
        return new ProductSummary(view.id(), view.name(), new Price(view.priceInclTax(), view.priceExclTax()), view.inStock())
                .imageUrl(view.imageUrl());
    }
}

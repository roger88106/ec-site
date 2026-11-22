package com.ecsite.module.storefront.infrastructure.directory;

import com.ecsite.common.conversion.EnumConversion;
import com.ecsite.common.model.id.ProductId;
import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.paging.PageResult;
import com.ecsite.module.catalog.api.CatalogApi;
import com.ecsite.module.catalog.api.model.CategoryNode;
import com.ecsite.module.catalog.api.model.ShownProductDetail;
import com.ecsite.module.catalog.api.model.ShownProductQuery;
import com.ecsite.module.catalog.api.model.ShownProductSort;
import com.ecsite.module.catalog.api.model.ShownProductSummary;
import com.ecsite.module.storefront.application.model.CatalogCategory;
import com.ecsite.module.storefront.application.model.CatalogProduct;
import com.ecsite.module.storefront.application.model.CatalogProductDetail;
import com.ecsite.module.storefront.application.model.ProductCondition;
import com.ecsite.module.storefront.application.port.CatalogDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 商品カタログの腐敗防止層
 *
 * <p>商品カタログの API を呼び、店舗の型に変換する。商品カタログの型を店舗の内部に持ち込まない。
 */
@Component
@RequiredArgsConstructor
class CatalogApiDirectory implements CatalogDirectory {

    /**
     * 商品カタログの API
     */
    private final CatalogApi catalogApi;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<CatalogProduct> listProducts(ProductCondition condition, PageRequest request) {
        var query = new ShownProductQuery(condition.categoryId(), condition.minPrice(), condition.maxPrice(), toSort(condition.sort()));
        return catalogApi.listShownProducts(query, request).map(CatalogApiDirectory::toProduct);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<CatalogProduct> searchProducts(String keyword, PageRequest request) {
        return catalogApi.searchShownProducts(keyword, request).map(CatalogApiDirectory::toProduct);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<CatalogProductDetail> findProduct(ProductId productId) {
        return catalogApi.findShownProduct(productId).map(CatalogApiDirectory::toDetail);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CatalogCategory> categoryTree() {
        return catalogApi.categoryTree().stream().map(CatalogApiDirectory::toCategory).toList();
    }

    /**
     * 店舗の並び順を、商品カタログの並び順に変換する。
     */
    static ShownProductSort toSort(ProductCondition.Sort sort) {
        return EnumConversion.byName(sort, ShownProductSort.class);
    }

// ============================== プライベートメソッド ==============================

    /**
     * 商品カタログの一覧の要素を、店舗の型に変換する。
     */
    private static CatalogProduct toProduct(ShownProductSummary summary) {
        return new CatalogProduct(summary.id(), summary.name(), summary.priceInclTax(), summary.priceExclTax(),
                summary.sellableVariantIds(), summary.imageUrl());
    }

    /**
     * 商品カタログの商品の詳細を、店舗の型に変換する（バリアントを含む）。
     */
    private static CatalogProductDetail toDetail(ShownProductDetail detail) {
        return new CatalogProductDetail(detail.id(), detail.name(), detail.description(), detail.priceInclTax(),
                detail.priceExclTax(), detail.axes(),
                detail.variants().stream()
                        .map(variant -> new CatalogProductDetail.Variant(variant.id(), variant.axisValues(),
                                variant.salesSuspended(), variant.imageUrl()))
                        .toList(),
                detail.imageUrls());
    }

    /**
     * 商品カタログのカテゴリを、子孫まで含めて店舗の型に変換する（再帰）。
     */
    private static CatalogCategory toCategory(CategoryNode node) {
        return new CatalogCategory(node.id(), node.name(),
                node.children().stream().map(CatalogApiDirectory::toCategory).toList());
    }
}

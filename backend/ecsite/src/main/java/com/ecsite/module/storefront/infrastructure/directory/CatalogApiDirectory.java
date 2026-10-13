package com.ecsite.module.storefront.infrastructure.directory;

import com.ecsite.module.storefront.application.port.CatalogDirectory;
import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.paging.PageResult;
import com.ecsite.module.catalog.api.StoreCatalogApi;
import com.ecsite.module.catalog.api.StoreCategoryNode;
import com.ecsite.module.catalog.api.StoreProductDetail;
import com.ecsite.module.catalog.api.StoreProductQuery;
import com.ecsite.module.catalog.api.StoreProductSort;
import com.ecsite.module.catalog.api.StoreProductSummary;
import com.ecsite.module.storefront.application.port.CatalogCategory;
import com.ecsite.module.storefront.application.port.CatalogProduct;
import com.ecsite.module.storefront.application.port.CatalogProductDetail;
import com.ecsite.module.storefront.application.port.ProductCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 商品カタログの腐敗防止層（バックエンドのアーキテクチャ 第 1 節、docs/backend/modules/storefront.md）。<br />
 * 商品カタログの API を呼び、店舗の型に変換する。商品カタログの型を店舗の内部に持ち込まない。
 */
@Component
@RequiredArgsConstructor
public class CatalogApiDirectory implements CatalogDirectory {

    /**
     * 店舗向けの商品カタログの API
     */
    private final StoreCatalogApi storeCatalogApi;

    /**
     * 商品一覧を取得する。
     *
     * @param condition 絞り込みと並び順
     * @param request   ページの指定
     * @return 商品の一覧
     */
    @Override
    public PageResult<CatalogProduct> listProducts(ProductCondition condition, PageRequest request) {
        var query = new StoreProductQuery(condition.categoryId(), condition.minPrice(), condition.maxPrice(),
                toSort(condition.sort()));
        return storeCatalogApi.listProducts(query, request).map(CatalogApiDirectory::toProduct);
    }

    /**
     * キーワードで商品を探す。
     *
     * @param keyword キーワード
     * @param request ページの指定
     * @return 検索結果
     */
    @Override
    public PageResult<CatalogProduct> searchProducts(String keyword, PageRequest request) {
        return storeCatalogApi.searchProducts(keyword, request).map(CatalogApiDirectory::toProduct);
    }

    /**
     * 商品の詳細を取得する。
     *
     * @param productId 商品 ID
     * @return 商品の詳細。表示されない商品なら空
     */
    @Override
    public Optional<CatalogProductDetail> findProduct(UUID productId) {
        return storeCatalogApi.findProduct(productId).map(CatalogApiDirectory::toDetail);
    }

    /**
     * カテゴリの木を取得する。
     *
     * @return 最上位カテゴリの一覧（子孫を含む）
     */
    @Override
    public List<CatalogCategory> categoryTree() {
        return storeCatalogApi.categoryTree().stream().map(CatalogApiDirectory::toCategory).toList();
    }

// ============================== プライベートメソッド ==============================

    /**
     * 店舗の並び順を、商品カタログの並び順に変換する。
     */
    private static StoreProductSort toSort(ProductCondition.Sort sort) {
        return switch (sort) {
            case NEWEST -> StoreProductSort.NEWEST;
            case PRICE_ASC -> StoreProductSort.PRICE_ASC;
            case PRICE_DESC -> StoreProductSort.PRICE_DESC;
        };
    }

    /**
     * 商品カタログの一覧の要素を、店舗の型に変換する。
     */
    private static CatalogProduct toProduct(StoreProductSummary summary) {
        return new CatalogProduct(summary.id(), summary.name(), summary.priceInclTax(), summary.priceExclTax(),
                summary.sellableVariantIds(), summary.imageUrl());
    }

    /**
     * 商品カタログの商品の詳細を、店舗の型に変換する（バリアントを含む）。
     */
    private static CatalogProductDetail toDetail(StoreProductDetail detail) {
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
    private static CatalogCategory toCategory(StoreCategoryNode node) {
        return new CatalogCategory(node.id(), node.name(), node.children().stream().map(CatalogApiDirectory::toCategory).toList());
    }
}

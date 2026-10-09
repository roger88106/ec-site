package com.ecsite.module.storefront.catalogdir;

import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.paging.PageResult;
import com.ecsite.module.catalog.api.StoreCatalogApi;
import com.ecsite.module.catalog.api.StoreCategoryNode;
import com.ecsite.module.catalog.api.StoreProductDetail;
import com.ecsite.module.catalog.api.StoreProductQuery;
import com.ecsite.module.catalog.api.StoreProductSort;
import com.ecsite.module.catalog.api.StoreProductSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 商品カタログの腐敗防止層（constitution 4.1、docs/modules/storefront.md）。<br />
 * 商品カタログの API を呼び、店舗の型に変換する。商品カタログの型を店舗の内部に持ち込まない。
 */
@Component
@RequiredArgsConstructor
public class CatalogDirectory {

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
    public PageResult<CatalogProduct> listProducts(ProductCondition condition, PageRequest request) {
        var query = new StoreProductQuery(condition.categoryId(), condition.minPrice(), condition.maxPrice(),
                toSort(condition.sort()));
        return storeCatalogApi.listProducts(query, request).map(CatalogDirectory::toProduct);
    }

    /**
     * キーワードで商品を探す。
     *
     * @param keyword キーワード
     * @param request ページの指定
     * @return 検索結果
     */
    public PageResult<CatalogProduct> searchProducts(String keyword, PageRequest request) {
        return storeCatalogApi.searchProducts(keyword, request).map(CatalogDirectory::toProduct);
    }

    /**
     * 商品の詳細を取得する。
     *
     * @param productId 商品 ID
     * @return 商品の詳細。表示されない商品なら空
     */
    public Optional<CatalogProductDetail> findProduct(UUID productId) {
        return storeCatalogApi.findProduct(productId).map(CatalogDirectory::toDetail);
    }

    /**
     * カテゴリの木を取得する。
     *
     * @return 最上位カテゴリの一覧（子孫を含む）
     */
    public List<CatalogCategory> categoryTree() {
        return storeCatalogApi.categoryTree().stream().map(CatalogDirectory::toCategory).toList();
    }

// ============================== プライベートメソッド ==============================

    private static StoreProductSort toSort(ProductCondition.Sort sort) {
        return switch (sort) {
            case NEWEST -> StoreProductSort.NEWEST;
            case PRICE_ASC -> StoreProductSort.PRICE_ASC;
            case PRICE_DESC -> StoreProductSort.PRICE_DESC;
        };
    }

    private static CatalogProduct toProduct(StoreProductSummary summary) {
        return new CatalogProduct(summary.id(), summary.name(), summary.priceInclTax(), summary.priceExclTax(),
                summary.sellableVariantIds());
    }

    private static CatalogProductDetail toDetail(StoreProductDetail detail) {
        return new CatalogProductDetail(detail.id(), detail.name(), detail.description(), detail.priceInclTax(),
                detail.priceExclTax(), detail.axes(),
                detail.variants().stream()
                        .map(variant -> new CatalogProductDetail.Variant(variant.id(), variant.axisValues(), variant.salesSuspended()))
                        .toList());
    }

    private static CatalogCategory toCategory(StoreCategoryNode node) {
        return new CatalogCategory(node.id(), node.name(), node.children().stream().map(CatalogDirectory::toCategory).toList());
    }
}

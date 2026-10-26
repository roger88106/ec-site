package com.ecsite.module.catalog.api;

import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.paging.PageResult;
import com.ecsite.module.catalog.application.StoreProductQueryService;
import com.ecsite.module.catalog.application.category.CategoryQueryService;
import com.ecsite.module.catalog.application.model.query.StoreProductListQuery;
import com.ecsite.module.catalog.application.model.view.CategoryNodeView;
import com.ecsite.module.catalog.application.model.view.StoreProductDetailView;
import com.ecsite.module.catalog.application.model.view.StoreProductSummaryView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 店舗向けの商品カタログの API（docs/design/modules/catalog.md「他のモジュールへの公開」）。<br />
 * 対象は C 側に表示される商品だけ。価格は税込・税抜の両方を返す。
 */
@Component
@RequiredArgsConstructor
public class StoreCatalogApi {

    /**
     * C 側向けの商品照会のクエリサービス
     */
    private final StoreProductQueryService storeProductQueryService;

    /**
     * カテゴリのクエリサービス
     */
    private final CategoryQueryService categoryQueryService;

    /**
     * 商品一覧を取得する。
     *
     * @param query   絞り込みと並び順
     * @param request ページの指定
     * @return 商品の一覧
     */
    public PageResult<StoreProductSummary> listProducts(StoreProductQuery query, PageRequest request) {
        return storeProductQueryService.listProducts(toListQuery(query), request).map(StoreCatalogApi::toSummary);
    }

    /**
     * キーワードで商品を探す。並びは新着順で固定。
     *
     * @param keyword キーワード
     * @param request ページの指定
     * @return 検索結果
     */
    public PageResult<StoreProductSummary> searchProducts(String keyword, PageRequest request) {
        return storeProductQueryService.searchProducts(keyword, request).map(StoreCatalogApi::toSummary);
    }

    /**
     * 商品の詳細を取得する。
     *
     * @param productId 商品 ID
     * @return 商品の詳細。表示されない商品なら空
     */
    public Optional<StoreProductDetail> findProduct(UUID productId) {
        return storeProductQueryService.findProduct(productId).map(StoreCatalogApi::toDetail);
    }

    /**
     * カテゴリの木を取得する（の絞り込み用）。
     *
     * @return 最上位カテゴリの一覧（子孫を含む。運営者が決めた並び順）
     */
    public List<StoreCategoryNode> categoryTree() {
        return categoryQueryService.getCategoryTree().stream().map(StoreCatalogApi::toNode).toList();
    }

// ============================== プライベートメソッド ==============================

    /**
     * カテゴリを、子孫まで含めて公開する型に変換する（再帰）。
     */
    private static StoreCategoryNode toNode(CategoryNodeView view) {
        return new StoreCategoryNode(view.id(), view.name(), view.children().stream().map(StoreCatalogApi::toNode).toList());
    }

    /**
     * 公開する条件を、application の条件に変換する。
     */
    private static StoreProductListQuery toListQuery(StoreProductQuery query) {
        return new StoreProductListQuery(query.categoryId(), query.minPrice(), query.maxPrice(), toSort(query.sort()));
    }

    /**
     * 公開する並び順を、application の並び順に変換する。
     * 単体テストから呼ぶので package-private。
     */
    static StoreProductListQuery.Sort toSort(StoreProductSort sort) {
        return switch (sort) {
            case NEWEST -> StoreProductListQuery.Sort.NEWEST;
            case PRICE_ASC -> StoreProductListQuery.Sort.PRICE_ASC;
            case PRICE_DESC -> StoreProductListQuery.Sort.PRICE_DESC;
        };
    }

    /**
     * 詳細の view を公開する型に変換する。
     */
    private static StoreProductDetail toDetail(StoreProductDetailView view) {
        return new StoreProductDetail(view.id(), view.name(), view.description(), view.priceInclTax(),
                view.priceExclTax(), view.axes(),
                view.variants().stream()
                        .map(variant -> new StoreProductDetail.Variant(variant.id(), variant.axisValues(),
                                variant.salesSuspended(), variant.imageUrl()))
                        .toList(),
                view.imageUrls());
    }

    /**
     * 一覧の view を公開する型に変換する。
     */
    private static StoreProductSummary toSummary(StoreProductSummaryView view) {
        return new StoreProductSummary(view.id(), view.name(), view.priceInclTax(), view.priceExclTax(),
                view.sellableVariantIds(), view.imageUrl());
    }
}

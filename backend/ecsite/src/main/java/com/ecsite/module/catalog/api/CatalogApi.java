package com.ecsite.module.catalog.api;

import com.ecsite.common.conversion.EnumConversion;
import com.ecsite.common.model.id.ProductId;
import com.ecsite.common.model.id.VariantId;
import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.paging.PageResult;
import com.ecsite.module.catalog.api.model.AxisValue;
import com.ecsite.module.catalog.api.model.CategoryNode;
import com.ecsite.module.catalog.api.model.ProductVariants;
import com.ecsite.module.catalog.api.model.ShownProductDetail;
import com.ecsite.module.catalog.api.model.ShownProductQuery;
import com.ecsite.module.catalog.api.model.ShownProductSort;
import com.ecsite.module.catalog.api.model.ShownProductSummary;
import com.ecsite.module.catalog.api.model.VariantDetail;
import com.ecsite.module.catalog.application.OrderableVariantQueryService;
import com.ecsite.module.catalog.application.StoreProductQueryService;
import com.ecsite.module.catalog.application.category.CategoryQueryService;
import com.ecsite.module.catalog.application.model.StoredProductDetail;
import com.ecsite.module.catalog.application.model.query.StoreProductListQuery;
import com.ecsite.module.catalog.application.model.view.CategoryNodeView;
import com.ecsite.module.catalog.application.model.view.OrderableVariantView;
import com.ecsite.module.catalog.application.model.view.ProductDetailView;
import com.ecsite.module.catalog.application.model.view.StoreProductDetailView;
import com.ecsite.module.catalog.application.model.view.StoreProductSummaryView;
import com.ecsite.module.catalog.application.product.ProductQueryService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * 商品カタログの API
 *
 * <p>商品とバリアントの参照は、店舗に表示されない商品・販売停止のバリアントも返す。
 * 名前に「表示」の語（{@code Shown}）を含むメソッドは、店舗に表示される商品だけを返す。
 */
@Component
@RequiredArgsConstructor
public class CatalogApi {

    /**
     * バリアントの詳細の参照
     */
    private final OrderableVariantQueryService variantQueryService;

    /**
     * 商品の参照
     */
    private final ProductQueryService productQueryService;

    /**
     * 店舗に表示される商品の参照
     */
    private final StoreProductQueryService shownProductQueryService;

    /**
     * カテゴリの参照
     */
    private final CategoryQueryService categoryQueryService;

    /**
     * 複数のバリアントの詳細を返す。
     *
     * <p>店舗に表示されない商品・販売停止のバリアントも返す。表示と販売停止の状態は項目で返す。
     *
     * @param variantIds バリアント ID
     * @return バリアント ID → 詳細。存在しないバリアントは含めない
     */
    public Map<VariantId, VariantDetail> findVariants(@NonNull Collection<VariantId> variantIds) {
        var result = new LinkedHashMap<VariantId, VariantDetail>();
        variantQueryService.findVariants(variantIds).forEach((variantId, view) -> result.put(variantId, toVariant(view)));
        return result;
    }

    /**
     * 商品と、その全てのバリアントを返す。表示の状態によらない。
     *
     * @param productId 商品 ID
     * @return 商品。なければ空
     */
    public Optional<ProductVariants> findProduct(@NonNull ProductId productId) {
        return productQueryService.findProduct(productId).map(CatalogApi::toProduct);
    }

    /**
     * 店舗に表示される商品の一覧を取得する。
     *
     * <p>価格は税込・税抜の両方を返す。
     *
     * @param query   絞り込みと並び順
     * @param request ページの指定
     * @return 商品の一覧
     */
    public PageResult<ShownProductSummary> listShownProducts(@NonNull ShownProductQuery query,
                                                             @NonNull PageRequest request) {
        return shownProductQueryService.listProducts(toListQuery(query), request).map(CatalogApi::toSummary);
    }

    /**
     * キーワードで、店舗に表示される商品を探す。
     *
     * <p>並びは新着順で固定。
     *
     * @param keyword キーワード
     * @param request ページの指定
     * @return 検索結果
     */
    public PageResult<ShownProductSummary> searchShownProducts(@NonNull String keyword, @NonNull PageRequest request) {
        return shownProductQueryService.searchProducts(keyword, request).map(CatalogApi::toSummary);
    }

    /**
     * 店舗に表示される商品の詳細を取得する。
     *
     * <p>価格は税込・税抜の両方を返す。
     *
     * @param productId 商品 ID
     * @return 商品の詳細。表示されない商品なら空
     */
    public Optional<ShownProductDetail> findShownProduct(@NonNull ProductId productId) {
        return shownProductQueryService.findProduct(productId).map(CatalogApi::toDetail);
    }

    /**
     * カテゴリの木を取得する。
     *
     * @return 最上位カテゴリの一覧（子孫を含む。運営者が決めた並び順）
     */
    public List<CategoryNode> categoryTree() {
        return categoryQueryService.getCategoryTree().stream().map(CatalogApi::toNode).toList();
    }

// ============================== プライベートメソッド ==============================

    /**
     * application の view を公開の型に変換する。
     */
    private static VariantDetail toVariant(OrderableVariantView view) {
        return new VariantDetail(view.variantId(), view.productId(), view.productCode(), view.variantCode(),
                view.productName(),
                view.axisValues().stream().map(axis -> new AxisValue(axis.name(), axis.value())).toList(),
                view.priceExclTax(), view.taxCategoryId(), view.imageUrl(), view.visible(), view.salesSuspended());
    }

    /**
     * application が返す、保存されている商品の詳細を、公開の型に変換する。
     */
    private static ProductVariants toProduct(StoredProductDetail product) {
        var variants = product.variants().stream()
                .map(variant -> toProductVariant(product.axes(), variant))
                .toList();
        return new ProductVariants(product.id(), product.name(), variants);
    }

    /**
     * バリアントを公開の型に変換する。
     *
     * <p>軸の名前と、バリアントの軸の値を、並びの位置で組にする。
     */
    private static ProductVariants.Variant toProductVariant(List<String> axes, ProductDetailView.VariantView variant) {
        var axisValues = IntStream.range(0, axes.size())
                .mapToObj(i -> new AxisValue(axes.get(i), variant.axisValues().get(i)))
                .toList();
        return new ProductVariants.Variant(variant.id(), variant.variantCode(), axisValues);
    }

    /**
     * カテゴリを、子孫まで含めて公開する型に変換する（再帰）。
     */
    private static CategoryNode toNode(CategoryNodeView view) {
        return new CategoryNode(view.id(), view.name(), view.children().stream().map(CatalogApi::toNode).toList());
    }

    /**
     * 公開する条件を、application の条件に変換する。
     */
    private static StoreProductListQuery toListQuery(ShownProductQuery query) {
        return new StoreProductListQuery(query.categoryId(), query.minPrice(), query.maxPrice(), toSort(query.sort()));
    }

    /**
     * 公開する並び順を、application の並び順に変換する。
     */
    static StoreProductListQuery.Sort toSort(ShownProductSort sort) {
        return EnumConversion.byName(sort, StoreProductListQuery.Sort.class);
    }

    /**
     * 詳細の view を公開する型に変換する。
     */
    private static ShownProductDetail toDetail(StoreProductDetailView view) {
        return new ShownProductDetail(view.id(), view.name(), view.description(), view.priceInclTax(),
                view.priceExclTax(), view.axes(),
                view.variants().stream()
                        .map(variant -> new ShownProductDetail.Variant(variant.id(), variant.axisValues(),
                                variant.salesSuspended(), variant.imageUrl()))
                        .toList(),
                view.imageUrls());
    }

    /**
     * 一覧の view を公開する型に変換する。
     */
    private static ShownProductSummary toSummary(StoreProductSummaryView view) {
        return new ShownProductSummary(view.id(), view.name(), view.priceInclTax(), view.priceExclTax(),
                view.sellableVariantIds(), view.imageUrl());
    }
}

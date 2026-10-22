package com.ecsite.module.storefront.presentation;

import com.ecsite.api.store.model.Amounts;
import com.ecsite.api.store.model.AxisValue;
import com.ecsite.api.store.model.Cart;
import com.ecsite.api.store.model.CategoryNode;
import com.ecsite.api.store.model.CategoryTree;
import com.ecsite.api.store.model.Price;
import com.ecsite.api.store.model.ProductDetail;
import com.ecsite.api.store.model.ProductSort;
import com.ecsite.api.store.model.ProductSummary;
import com.ecsite.api.store.model.ProductSummaryPage;
import com.ecsite.api.store.model.QuoteItem;
import com.ecsite.api.store.model.Shop;
import com.ecsite.api.store.model.TaxAmount;
import com.ecsite.api.store.model.UnpurchasableReason;
import com.ecsite.api.store.model.Variant;
import com.ecsite.api.store.model.VariantAxis;
import com.ecsite.common.model.value.Money;
import com.ecsite.common.paging.PageResult;
import com.ecsite.common.presentation.StorePrices;
import com.ecsite.module.storefront.application.ProductDetailView;
import com.ecsite.module.storefront.application.ProductSummaryView;
import com.ecsite.module.storefront.application.ShopProperties;
import com.ecsite.module.storefront.application.port.CartView;
import com.ecsite.module.storefront.application.port.CatalogCategory;
import com.ecsite.module.storefront.application.port.ProductCondition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 店舗 API のリクエスト・レスポンスと、店舗のサービスのモデルの変換。
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
     * 商品一覧の条件を作る。
     *
     * @param categoryId カテゴリ ID
     * @param minPrice   税込価格の下限（円）
     * @param maxPrice   税込価格の上限（円）
     * @param sort       並び順
     * @return 商品一覧の条件
     */
    ProductCondition toProductCondition(UUID categoryId, Long minPrice, Long maxPrice, ProductSort sort) {
        return new ProductCondition(categoryId, minPrice == null ? null : Money.of(minPrice),
                maxPrice == null ? null : Money.of(maxPrice), toSort(sort));
    }

    /**
     * カテゴリの木のレスポンスに変換する。
     *
     * @param categories カテゴリの木の根の節
     * @return カテゴリの木のレスポンス
     */
    CategoryTree toCategoryTree(List<CatalogCategory> categories) {
        return new CategoryTree().items(categories.stream().map(this::toCategoryNode).toList());
    }

    /**
     * 商品一覧のレスポンスに変換する。
     *
     * @param page 商品の一覧
     * @return 商品一覧のレスポンス
     */
    ProductSummaryPage toProductSummaryPage(PageResult<ProductSummaryView> page) {
        return new ProductSummaryPage()
                .page(page.page())
                .size(page.size())
                .totalItems(page.totalItems())
                .totalPages(page.totalPages())
                .items(page.items().stream().map(StorefrontMapper::toProductSummary).toList());
    }

    /**
     * 商品の詳細のレスポンスに変換する。
     *
     * @param view 商品の詳細
     * @return 商品の詳細のレスポンス
     */
    ProductDetail toProductDetail(ProductDetailView view) {
        return new ProductDetail()
                .id(view.id())
                .name(view.name())
                .description(view.description())
                .price(StorePrices.of(view.priceInclTax(), view.priceExclTax()))
                .imageUrls(view.imageUrls())
                .axes(view.axes().stream().map(StorefrontMapper::toVariantAxis).toList())
                .variants(view.variants().stream().map(StorefrontMapper::toVariant).toList());
    }

    /**
     * カテゴリの木の節のレスポンスに変換する。
     *
     * @param category カテゴリ
     * @return カテゴリの木の節のレスポンス
     */
    CategoryNode toCategoryNode(CatalogCategory category) {
        return new CategoryNode()
                .id(category.id())
                .name(category.name())
                .children(category.children().stream().map(this::toCategoryNode).toList());
    }

    /**
     * カートの表示のレスポンスに変換する。
     *
     * @param view カートの表示
     * @return カートのレスポンス
     */
    Cart toCart(CartView view) {
        return new Cart()
                .id(view.cartId())
                .items(view.items().stream().map(StorefrontMapper::toQuoteItem).toList())
                .amounts(toAmounts(view.amounts()))
                .orderable(view.orderable());
    }

    /**
     * 店舗の情報のレスポンスに変換する。
     *
     * @param properties 店舗の情報の設定
     * @return 店舗の情報のレスポンス
     */
    Shop toShop(ShopProperties properties) {
        return new Shop()
                .contactEmail(properties.contactEmail());
    }

// ============================== プライベートメソッド ==============================

    /**
     * 一覧の商品をレスポンスに変換する。代表画像がなければ null。
     */
    private static ProductSummary toProductSummary(ProductSummaryView view) {
        return new ProductSummary()
                .id(view.id())
                .name(view.name())
                .price(StorePrices.of(view.priceInclTax(), view.priceExclTax()))
                .inStock(view.inStock())
                .imageUrl(view.imageUrl());
    }

    /**
     * 商品の軸のレスポンスに変換する。
     */
    private static VariantAxis toVariantAxis(ProductDetailView.AxisView axis) {
        return new VariantAxis().name(axis.name()).values(axis.values());
    }

    /**
     * バリアントのレスポンスに変換する。画像がなければ null。
     */
    private static Variant toVariant(ProductDetailView.VariantView variant) {
        return new Variant()
                .id(variant.id())
                .axisValues(variant.axisValues())
                .inStock(variant.inStock())
                .salesSuspended(variant.salesSuspended())
                .imageUrl(variant.imageUrl());
    }

    /**
     * カートの品目のレスポンスに変換する。画像と購入できない理由は、null でも項目として返す。
     */
    private static QuoteItem toQuoteItem(CartView.Item item) {
        var reason = item.unpurchasableReason();
        var purchasable = reason == null;
        return new QuoteItem()
                .variantId(item.variantId())
                .productId(item.productId())
                .productName(item.productName())
                .axisValues(item.axisValues().stream().map(StorefrontMapper::toAxisValue).toList())
                .imageUrl(item.imageUrl())
                .unitPrice(new Price().inclTax(item.unitPriceInclTax().yen()).exclTax(item.unitPriceExclTax().yen()))
                .quantity(item.quantity())
                .subtotal(item.subtotal().yen())
                .purchasable(purchasable)
                .unpurchasableReason(reason == null ? null : toUnpurchasableReason(reason));
    }

    /**
     * 購入できない理由のレスポンスに変換する。
     */
    static UnpurchasableReason toUnpurchasableReason(CartView.UnpurchasableReason reason) {
        return switch (reason) {
            case NOT_SHOWN -> UnpurchasableReason.NOT_SHOWN;
            case SALES_SUSPENDED -> UnpurchasableReason.SALES_SUSPENDED;
            case OUT_OF_STOCK -> UnpurchasableReason.OUT_OF_STOCK;
            case INSUFFICIENT_STOCK -> UnpurchasableReason.INSUFFICIENT_STOCK;
        };
    }

    /**
     * カートの金額の内訳のレスポンスに変換する。
     */
    private static Amounts toAmounts(CartView.Amounts amounts) {
        return new Amounts()
                .itemsTotal(amounts.itemsTotal().yen())
                .shippingFee(amounts.shippingFee().yen())
                .total(amounts.total().yen())
                .taxes(amounts.taxes().stream().map(StorefrontMapper::toTaxAmount).toList());
    }

    /**
     * 1 つの税率の消費税額のレスポンスに変換する。
     */
    private static TaxAmount toTaxAmount(CartView.TaxAmount tax) {
        return new TaxAmount()
                .ratePercent(tax.ratePercent())
                .taxableAmount(tax.taxableAmount().yen())
                .taxAmount(tax.taxAmount().yen());
    }

    /**
     * 軸の名前と値のレスポンスに変換する。
     */
    private static AxisValue toAxisValue(CartView.AxisValue axis) {
        return new AxisValue()
                .name(axis.name())
                .value(axis.value());
    }
}

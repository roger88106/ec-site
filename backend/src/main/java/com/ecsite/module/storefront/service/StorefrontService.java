package com.ecsite.module.storefront.service;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.paging.PageResult;
import com.ecsite.module.storefront.catalogdir.CatalogCategory;
import com.ecsite.module.storefront.catalogdir.CatalogDirectory;
import com.ecsite.module.storefront.catalogdir.CatalogProduct;
import com.ecsite.module.storefront.catalogdir.CatalogProductDetail;
import com.ecsite.module.storefront.catalogdir.ProductCondition;
import com.ecsite.module.storefront.stockdir.StockDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * 店舗のサービス。商品カタログと在庫の結果を組み合わせる（docs/modules/storefront.md）。<br />
 * 商品・価格・公開状態・在庫はキャッシュしない（NFR-1）。
 */
@Service
@RequiredArgsConstructor
public class StorefrontService {

    /**
     * 商品カタログの腐敗防止層
     */
    private final CatalogDirectory catalogDirectory;

    /**
     * 在庫の腐敗防止層
     */
    private final StockDirectory stockDirectory;

    /**
     * 商品一覧を取得する（AC-51〜57）。
     *
     * @param condition 絞り込みと並び順
     * @param request   ページの指定
     * @return 商品の一覧（在庫の有無を含む）
     */
    public PageResult<ProductSummaryView> listProducts(ProductCondition condition, PageRequest request) {
        return withStock(catalogDirectory.listProducts(condition, request));
    }

    /**
     * キーワードで商品を探す（AC-58〜61）。
     *
     * @param keyword キーワード
     * @param request ページの指定
     * @return 検索結果（在庫の有無を含む）
     */
    public PageResult<ProductSummaryView> searchProducts(String keyword, PageRequest request) {
        return withStock(catalogDirectory.searchProducts(keyword, request));
    }

    /**
     * 商品の詳細を取得する（AC-62〜65）。表示されない商品は、存在しない商品と同じく見つからない。
     *
     * @param productId 商品 ID
     * @return 商品の詳細（バリアントごとの在庫の有無を含む）
     */
    public ProductDetailView getProduct(UUID productId) {
        var detail = catalogDirectory.findProduct(productId)
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND));
        var inStock = stockDirectory.inStock(detail.variants().stream().map(CatalogProductDetail.Variant::id).toList());

        var variants = detail.variants().stream()
                .map(variant -> new ProductDetailView.VariantView(variant.id(), variant.axisValues(),
                        inStock.contains(variant.id()), variant.salesSuspended()))
                .toList();
        return new ProductDetailView(detail.id(), detail.name(), detail.description(), detail.priceInclTax(),
                detail.priceExclTax(), axes(detail), variants);
    }

    /**
     * カテゴリの木を取得する。
     *
     * @return 最上位カテゴリの一覧（子孫を含む）
     */
    public List<CatalogCategory> categoryTree() {
        return catalogDirectory.categoryTree();
    }

// ============================== プライベートメソッド ==============================

    /**
     * 1 ページ分の商品に、在庫の有無を付ける。販売中のいずれかのバリアントに在庫があれば「在庫あり」（AC-56）。<br />
     * 在庫の有無は、ページのすべてのバリアントについてまとめて 1 回で問い合わせる。
     *
     * @param page 商品の一覧
     * @return 在庫の有無を付けた一覧
     */
    private PageResult<ProductSummaryView> withStock(PageResult<CatalogProduct> page) {
        var inStock = stockDirectory.inStock(page.items().stream()
                .flatMap(product -> product.sellableVariantIds().stream())
                .toList());
        return page.map(product -> new ProductSummaryView(product.id(), product.name(), product.priceInclTax(),
                product.priceExclTax(), product.sellableVariantIds().stream().anyMatch(inStock::contains)));
    }

    /**
     * バリアント軸と、その値のすべてを求める（AC-62）。値は、バリアントの表示順で最初に現れた順。
     * 在庫切れ・販売停止で選べない値も含める。
     *
     * @param detail 商品の詳細
     * @return バリアント軸と値
     */
    private static List<ProductDetailView.AxisView> axes(CatalogProductDetail detail) {
        return IntStream.range(0, detail.axes().size())
                .mapToObj(position -> {
                    var values = new LinkedHashSet<String>();
                    detail.variants().forEach(variant -> values.add(variant.axisValues().get(position)));
                    return new ProductDetailView.AxisView(detail.axes().get(position), List.copyOf(values));
                })
                .toList();
    }
}

package com.ecsite.module.storefront.presentation;

import com.ecsite.api.store.ProductApi;
import com.ecsite.api.store.model.ProductDetail;
import com.ecsite.api.store.model.ProductSort;
import com.ecsite.api.store.model.ProductSummaryPage;
import com.ecsite.common.paging.PageRequest;
import com.ecsite.module.storefront.application.StorefrontService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 店舗の商品のコントローラー（C 側。store.yaml の tag: product）
 */
@RestController
@RequiredArgsConstructor
class StorefrontProductController implements ProductApi {

    /**
     * 店舗のサービス
     */
    private final StorefrontService storefrontService;

    /**
     * 店舗 API のマッパー
     */
    private final StorefrontMapper mapper;

    /**
     * 商品一覧
     *
     * @param categoryId カテゴリ ID（子孫のカテゴリを含む）
     * @param minPrice   税込価格の下限
     * @param maxPrice   税込価格の上限
     * @param sort       並び順
     * @param page       ページ番号
     * @param size       1 ページの件数
     * @return Http 200 : {@link ProductSummaryPage}
     */
    @Override
    public ResponseEntity<ProductSummaryPage> listProducts(@Nullable UUID categoryId, @Nullable Long minPrice, @Nullable Long maxPrice,
                                                           ProductSort sort, Integer page, Integer size) {
        var condition = mapper.toProductCondition(categoryId, minPrice, maxPrice, sort);
        var result = storefrontService.listProducts(condition, PageRequest.of(page, size));
        return ResponseEntity.ok(mapper.toProductSummaryPage(result));
    }

    /**
     * キーワード検索
     *
     * @param q    キーワード
     * @param page ページ番号
     * @param size 1 ページの件数
     * @return Http 200 : {@link ProductSummaryPage}
     */
    @Override
    public ResponseEntity<ProductSummaryPage> searchProducts(String q, Integer page, Integer size) {
        var result = storefrontService.searchProducts(q, PageRequest.of(page, size));
        return ResponseEntity.ok(mapper.toProductSummaryPage(result));
    }

    /**
     * 商品の詳細。表示されない商品は 404
     *
     * @param productId 商品 ID
     * @return Http 200 : {@link ProductDetail}
     */
    @Override
    public ResponseEntity<ProductDetail> getProduct(UUID productId) {
        return ResponseEntity.ok(mapper.toProductDetail(storefrontService.getProduct(productId)));
    }
}

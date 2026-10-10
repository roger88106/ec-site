package com.ecsite.module.catalog.api;

import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.paging.PageResult;
import com.ecsite.module.catalog.application.CategoryQueryService;
import com.ecsite.module.catalog.application.StoreProductQueryService;
import com.ecsite.module.catalog.application.model.view.CategoryNodeView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 店舗向けの商品カタログの API（docs/modules/catalog.md「他モジュールへの公開」）。<br />
 * 対象は C 側に表示される商品だけ（BR-2）。価格は税込・税抜の両方を返す。
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
     * 商品一覧を取得する（AC-51〜54、57）。
     *
     * @param query   絞り込みと並び順
     * @param request ページの指定
     * @return 商品の一覧
     */
    public PageResult<StoreProductSummary> listProducts(StoreProductQuery query, PageRequest request) {
        return storeProductQueryService.listProducts(query, request);
    }

    /**
     * キーワードで商品を探す（AC-58〜61）。並びは新着順で固定。
     *
     * @param keyword キーワード
     * @param request ページの指定
     * @return 検索結果
     */
    public PageResult<StoreProductSummary> searchProducts(String keyword, PageRequest request) {
        return storeProductQueryService.searchProducts(keyword, request);
    }

    /**
     * 商品の詳細を取得する（AC-62、65）。
     *
     * @param productId 商品 ID
     * @return 商品の詳細。表示されない商品なら空
     */
    public Optional<StoreProductDetail> findProduct(UUID productId) {
        return storeProductQueryService.findProduct(productId);
    }

    /**
     * カテゴリの木を取得する（AC-52 の絞り込み用）。
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
}

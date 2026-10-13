package com.ecsite.module.storefront.application.port;

import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.paging.PageResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 商品カタログの腐敗防止層の口（バックエンドのアーキテクチャ 第 1 節、docs/backend/modules/storefront.md）。<br />
 * 商品とカテゴリを、店舗の型で返す。
 */
public interface CatalogDirectory {

    /**
     * 商品一覧を取得する。
     *
     * @param condition 絞り込みと並び順
     * @param request   ページの指定
     * @return 商品の一覧
     */
    PageResult<CatalogProduct> listProducts(ProductCondition condition, PageRequest request);

    /**
     * キーワードで商品を探す。
     *
     * @param keyword キーワード
     * @param request ページの指定
     * @return 検索結果
     */
    PageResult<CatalogProduct> searchProducts(String keyword, PageRequest request);

    /**
     * 商品の詳細を取得する。
     *
     * @param productId 商品 ID
     * @return 商品の詳細。表示されない商品なら空
     */
    Optional<CatalogProductDetail> findProduct(UUID productId);

    /**
     * カテゴリの木を取得する。
     *
     * @return 最上位カテゴリの一覧（子孫を含む）
     */
    List<CatalogCategory> categoryTree();
}

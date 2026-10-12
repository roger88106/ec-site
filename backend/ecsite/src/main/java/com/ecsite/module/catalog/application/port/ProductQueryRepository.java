package com.ecsite.module.catalog.application;

import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.paging.PageResult;
import com.ecsite.module.catalog.application.model.query.ProductListQuery;
import com.ecsite.module.catalog.application.model.view.ProductDetailView;
import com.ecsite.module.catalog.application.model.view.ProductSummaryView;

import java.util.Optional;
import java.util.UUID;

/**
 * 商品照会のクエリリポジトリ。<br />
 * 返すビューの税込価格は空のまま。時刻と税率で決まるので、クエリサービスが設定する。
 */
public interface ProductQueryRepository {

    /**
     * 商品の詳細を取得する。
     *
     * @param id 商品 ID
     * @return 商品の詳細（バリアントは表示順）。なければ空
     */
    Optional<ProductDetailView> findDetailById(UUID id);

    /**
     * B 側の商品一覧を取得する（AC-47〜50）。状態に関係なく、すべての商品が対象。<br />
     * 並びは登録日時の新しい順で、同じなら ID の順。
     *
     * @param query   絞り込みの条件
     * @param request ページの指定
     * @return 商品の一覧
     */
    PageResult<ProductSummaryView> findSummaries(ProductListQuery query, PageRequest request);
}

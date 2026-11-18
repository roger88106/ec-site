package com.ecsite.module.catalog.application.port;

import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.paging.PageResult;
import com.ecsite.module.catalog.application.model.StoredProductDetail;
import com.ecsite.module.catalog.application.model.query.ProductListQuery;
import com.ecsite.module.catalog.application.model.view.ProductSummaryView;

import java.util.Optional;
import java.util.UUID;

/**
 * 商品照会のクエリリポジトリ
 *
 * <p>商品の詳細は、保存されている値だけの型で返す（税込価格とできる操作は、時刻で決まるのでサービスが求める）。
 */
public interface ProductQueryRepository {

    /**
     * 商品の詳細を取得する。
     *
     * @param id 商品 ID
     * @return 商品の詳細（バリアントは表示順）。なければ空
     */
    Optional<StoredProductDetail> findDetailById(UUID id);

    /**
     * B 側の商品一覧を取得する。
     *
     * <p>状態に関係なく、全ての商品が対象。並びは登録日時の新しい順で、同じなら ID の順。
     *
     * @param query   絞り込みの条件
     * @param request ページの指定
     * @return 商品の一覧
     */
    PageResult<ProductSummaryView> findSummaries(ProductListQuery query, PageRequest request);
}

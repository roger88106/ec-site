package com.ecsite.module.catalog.application.port;

import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.paging.PageResult;
import com.ecsite.module.catalog.api.StoreProductDetail;
import com.ecsite.module.catalog.api.StoreProductQuery;
import com.ecsite.module.catalog.api.StoreProductSummary;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * C 側向けの商品照会のクエリリポジトリ。<br />
 * 対象は C 側に表示される商品だけ。税込価格は、渡された税区分ごとの率で SQL で計算する（docs/backend/modules/catalog.md）。
 * 率のない税区分の商品は、税込価格を出せないので対象にしない。
 */
public interface StoreProductQueryRepository {

    /**
     * C 側の商品一覧を取得する。
     *
     * @param query   絞り込みと並び順
     * @param request ページの指定
     * @param now     表示時刻
     * @param rates   税区分 ID → 表示時刻で有効な率（%）
     * @return 商品の一覧
     */
    PageResult<StoreProductSummary> findVisible(StoreProductQuery query, PageRequest request, Instant now,
                                                Map<UUID, BigDecimal> rates);

    /**
     * C 側の商品の詳細を取得する。
     *
     * @param id    商品 ID
     * @param now   表示時刻
     * @param rates 税区分 ID → 表示時刻で有効な率（%）
     * @return 商品の詳細。表示されない商品なら空
     */
    Optional<StoreProductDetail> findVisibleDetail(UUID id, Instant now, Map<UUID, BigDecimal> rates);
}

package com.ecsite.module.catalog.application.port;

import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.paging.PageResult;
import com.ecsite.module.catalog.application.model.view.StoreProductSummaryView;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 商品検索
 *
 * <p>キーワード検索の port。対象は C 側に表示される商品だけ。並びは新着順で固定。精度は問わない。
 */
public interface ProductSearch {

    /**
     * キーワードで商品を探す。
     *
     * @param keyword キーワード
     * @param request ページの指定
     * @param now     表示時刻
     * @param rates   税区分 ID → 表示時刻で有効な率（%）
     * @return 検索結果（新着順）
     */
    PageResult<StoreProductSummaryView> search(String keyword, PageRequest request, Instant now, Map<UUID, BigDecimal> rates);
}

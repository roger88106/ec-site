package com.ecsite.module.catalog.infrastructure.persistence;

import com.ecsite.common.model.id.TaxCategoryId;
import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.paging.PageResult;
import com.ecsite.module.catalog.application.model.view.StoreProductSummaryView;
import com.ecsite.module.catalog.application.port.ProductSearch;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.ecsite.jooq.catalog.Tables.PRODUCT;

/**
 * PostgreSQL による商品検索
 *
 * <p>簡易な実装。商品名と説明に対する部分一致（英字の大文字・小文字は区別しない）。キーワードの中のワイルドカード文字はエスケープする。
 * 並びは新着順で固定し、専用のインデックスは作らない。
 */
@Repository
@RequiredArgsConstructor
class PostgresProductSearch implements ProductSearch {

    /**
     * C 側で商品を 1 ページずつ取得する共通の処理
     */
    private final StoreProductPages storeProductPages;

    /**
     * キーワードで商品を探す。
     *
     * @param keyword キーワード
     * @param request ページの指定
     * @param now     表示時刻
     * @param rates   税区分 ID → 率（%）
     * @return 検索結果（新着順）
     */
    @Override
    public PageResult<StoreProductSummaryView> search(String keyword, PageRequest request, Instant now,
                                                  Map<TaxCategoryId, BigDecimal> rates) {
        var pattern = LikePatterns.contains(keyword);
        return storeProductPages.find(request, now, rates,
                priceInclTax -> PRODUCT.NAME.likeIgnoreCase(pattern, LikePatterns.ESCAPE)
                        .or(PRODUCT.DESCRIPTION.likeIgnoreCase(pattern, LikePatterns.ESCAPE)),
                priceInclTax -> List.of(PRODUCT.FIRST_SHOWN_AT.desc(), PRODUCT.ID));
    }
}

package com.ecsite.module.catalog.infrastructure.persistence;

import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.paging.PageResult;
import com.ecsite.common.persistence.LikePatterns;
import com.ecsite.module.catalog.api.StoreProductSummary;
import com.ecsite.module.catalog.application.port.ProductSearch;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ecsite.jooq.catalog.Tables.PRODUCT;

/**
 * 商品検索の PostgreSQL による簡易実装（constitution 4.4）。<br />
 * 商品名と説明に対する部分一致（英字の大文字・小文字は区別しない）。キーワード中のワイルドカード文字はエスケープする。
 * 並びは新着順で固定し、専用のインデックスは作らない（docs/modules/catalog.md）。
 */
@Repository
@RequiredArgsConstructor
class PostgresProductSearch implements ProductSearch {

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

    /**
     * C 側の一覧・検索の 1 ページ分の商品の組み立て
     */
    private final StoreProductSummaries storeProductSummaries;

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
    public PageResult<StoreProductSummary> search(String keyword, PageRequest request, Instant now,
                                                  Map<UUID, BigDecimal> rates) {
        // 有効な税率がひとつもなければ、どの商品も税込価格を出せない（表示できない）
        if (rates.isEmpty()) {
            return PageResult.of(List.of(), request, 0);
        }
        var priceInclTax = PriceInclTaxField.of(rates);
        var pattern = LikePatterns.contains(keyword);
        // 表示条件は一覧と同じ部品を使う（検索だけに非公開の商品が出る事故を防ぐ）
        var condition = ProductVisibility.visibleAt(now)
                .and(priceInclTax.isNotNull())
                .and(PRODUCT.NAME.likeIgnoreCase(pattern, LikePatterns.ESCAPE)
                        .or(PRODUCT.DESCRIPTION.likeIgnoreCase(pattern, LikePatterns.ESCAPE)));

        var rows = dsl.select(PRODUCT.ID, PRODUCT.NAME, priceInclTax, PRODUCT.PRICE_EXCL_TAX)
                .from(PRODUCT)
                .where(condition)
                .orderBy(PRODUCT.FIRST_SHOWN_AT.desc(), PRODUCT.ID)
                .limit(request.size())
                .offset(request.offset())
                .fetch();

        var items = storeProductSummaries.of(rows);

        return PageResult.of(items, request, dsl.fetchCount(PRODUCT, condition));
    }
}

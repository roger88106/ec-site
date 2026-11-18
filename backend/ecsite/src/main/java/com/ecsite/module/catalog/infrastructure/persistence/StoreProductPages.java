package com.ecsite.module.catalog.infrastructure.persistence;

import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.paging.PageResult;
import com.ecsite.module.catalog.application.model.view.StoreProductSummaryView;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static com.ecsite.jooq.catalog.Tables.PRODUCT;

/**
 * C 側で商品を 1 ページずつ取得する共通の処理
 *
 * <p>一覧と検索で共通の処理（表示条件・列・ページング・要約の組み立て・件数）をまとめる。
 * 呼び出し元は、絞り込みの条件と並び順だけを渡す。
 */
@Component
@RequiredArgsConstructor
class StoreProductPages {

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

    /**
     * C 側の商品の要約を組み立てる処理
     */
    private final StoreProductSummaries storeProductSummaries;

    /**
     * 表示される商品の 1 ページ分を取得する。
     *
     * <p>呼び出し元の絞り込みに、表示される商品であることと、税込価格を出せることを加える。
     *
     * @param request ページの指定
     * @param now     表示時刻
     * @param rates   税区分 ID → 率（%）
     * @param filter  税込価格の式から、絞り込みの条件を作る関数
     * @param orderBy 税込価格の式から、並び順を作る関数（同じ値は ID の順にする）
     * @return 商品の 1 ページ分
     */
    PageResult<StoreProductSummaryView> find(PageRequest request, Instant now, Map<UUID, BigDecimal> rates,
                                             Function<Field<Long>, Condition> filter,
                                             Function<Field<Long>, Collection<? extends OrderField<?>>> orderBy) {
        // 有効な税率がひとつもなければ、どの商品も税込価格を出せない（表示できない）
        if (rates.isEmpty()) {
            return PageResult.of(List.of(), request, 0);
        }
        var priceInclTax = PriceInclTaxField.of(rates);
        var condition = visible(now, priceInclTax).and(filter.apply(priceInclTax));

        var rows = dsl.select(PRODUCT.ID, PRODUCT.NAME, priceInclTax, PRODUCT.PRICE_EXCL_TAX)
                .from(PRODUCT)
                .where(condition)
                .orderBy(orderBy.apply(priceInclTax))
                .limit(request.size())
                .offset(request.offset())
                .fetch();

        var items = storeProductSummaries.of(rows, priceInclTax);

        return PageResult.of(items, request, dsl.fetchCount(PRODUCT, condition));
    }

    /**
     * 表示される商品で、税込価格を出せる条件。
     *
     * <p>一覧・検索・1 件の取得で同じ式を使う（一部だけに非公開の商品が出る事故を防ぐ）。
     *
     * @param now          表示時刻
     * @param priceInclTax 税込価格の式
     * @return 表示の条件
     */
    static Condition visible(Instant now, Field<Long> priceInclTax) {
        return ProductVisibility.visibleAt(now).and(priceInclTax.isNotNull());
    }
}

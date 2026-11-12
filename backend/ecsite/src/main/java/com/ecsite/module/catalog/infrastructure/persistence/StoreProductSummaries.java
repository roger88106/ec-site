package com.ecsite.module.catalog.infrastructure.persistence;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.catalog.application.model.view.StoreProductSummaryView;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Result;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.ecsite.jooq.catalog.Tables.PRODUCT;

/**
 * C 側の一覧の 1 ページ分の商品の組み立て
 *
 * <p>販売中のバリアントと代表画像を、ページの商品についてまとめて取得する（商品ごとに問い合わせない）。
 */
@Component
@RequiredArgsConstructor
class StoreProductSummaries {

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

    /**
     * C 側に返す商品画像の URL
     */
    private final StoreImageUrls storeImageUrls;

    /**
     * 1 ページ分の行から、一覧の商品を作る。
     *
     * @param rows         商品 ID、商品名、税込価格、税抜価格の行（表示する順）
     * @param priceInclTax 行の税込価格の式
     * @return 一覧の商品（行と同じ順）
     */
    List<StoreProductSummaryView> of(Result<? extends Record> rows, Field<Long> priceInclTax) {
        var productIds = rows.map(row -> row.get(PRODUCT.ID));
        var sellable = SellableVariants.byProduct(dsl, productIds);
        var images = storeImageUrls.representatives(productIds);
        return rows.map(row -> {
            var productId = row.get(PRODUCT.ID);
            return new StoreProductSummaryView(productId, row.get(PRODUCT.NAME), Money.of(row.get(priceInclTax)),
                    Money.of(row.get(PRODUCT.PRICE_EXCL_TAX)), sellable.getOrDefault(productId, List.of()), images.get(productId));
        });
    }
}

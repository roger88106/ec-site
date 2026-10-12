package com.ecsite.module.catalog.infrastructure.persistence;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.catalog.api.StoreProductSummary;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record4;
import org.jooq.Result;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * C 側の一覧・検索の 1 ページ分の商品の組み立て。一覧と検索で共通。<br />
 * 販売中のバリアント（在庫の有無の判定に使う。AC-56）と代表画像（AC-37）を、ページの商品についてまとめて取得する
 * （商品ごとに問い合わせない）。
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
     * @param rows 商品 ID、商品名、税込価格、税抜価格の行（表示する順）
     * @return 一覧の商品（行と同じ順）
     */
    List<StoreProductSummary> of(Result<Record4<UUID, String, Long, Long>> rows) {
        var productIds = rows.map(Record4::value1);
        var sellable = SellableVariants.byProduct(dsl, productIds);
        var images = storeImageUrls.representatives(productIds);
        return rows.map(row -> new StoreProductSummary(row.value1(), row.value2(), Money.of(row.value3()), Money.of(row.value4()),
                sellable.getOrDefault(row.value1(), List.of()), images.get(row.value1())));
    }
}

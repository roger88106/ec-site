package com.ecsite.module.catalog.infrastructure;

import org.jooq.DSLContext;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ecsite.jooq.catalog.Tables.VARIANT;

/**
 * 販売中（販売停止でない）のバリアントの取得。C 側の一覧と検索で共通（在庫の有無の判定に使う。AC-56）。
 */
final class SellableVariants {

    /**
     * インスタンスを作らせない（static のメソッドだけを持つ）
     */
    private SellableVariants() {
    }

    /**
     * 販売中のバリアントの ID を、商品ごとに表示順で取得する。
     *
     * @param dsl        jOOQ DSL Context
     * @param productIds 商品 ID
     * @return 商品 ID → バリアント ID。販売中のバリアントがない商品は含まない
     */
    static Map<UUID, List<UUID>> byProduct(DSLContext dsl, Collection<UUID> productIds) {
        return dsl.select(VARIANT.PRODUCT_ID, VARIANT.ID)
                .from(VARIANT)
                .where(VARIANT.PRODUCT_ID.in(productIds))
                .and(VARIANT.SALES_SUSPENDED.isFalse())
                .orderBy(VARIANT.PRODUCT_ID, VARIANT.DISPLAY_ORDER)
                .fetchGroups(VARIANT.PRODUCT_ID, VARIANT.ID);
    }
}

package com.ecsite.module.catalog.infrastructure.persistence;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jooq.DSLContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ecsite.jooq.catalog.Tables.PRODUCT_AXIS;
import static com.ecsite.jooq.catalog.Tables.VARIANT;
import static com.ecsite.jooq.catalog.Tables.VARIANT_AXIS_VALUE;

/**
 * 商品の一部の取得
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ProductParts {

    /**
     * バリアント軸の名前を並び順で取得する。
     *
     * @param dsl       jOOQ DSL Context
     * @param productId 商品 ID
     * @return 軸の名前
     */
    static List<String> axes(DSLContext dsl, UUID productId) {
        return dsl.select(PRODUCT_AXIS.NAME)
                .from(PRODUCT_AXIS)
                .where(PRODUCT_AXIS.PRODUCT_ID.eq(productId))
                .orderBy(PRODUCT_AXIS.POSITION)
                .fetch(PRODUCT_AXIS.NAME);
    }

    /**
     * バリアントごとの軸の値を、軸の並びで取得する。
     *
     * @param dsl       jOOQ DSL Context
     * @param productId 商品 ID
     * @return バリアント ID → 軸の値（軸の並び）
     */
    static Map<UUID, List<String>> axisValuesByVariant(DSLContext dsl, UUID productId) {
        return dsl.select(VARIANT_AXIS_VALUE.VARIANT_ID, VARIANT_AXIS_VALUE.VALUE)
                .from(VARIANT_AXIS_VALUE)
                .join(VARIANT).on(VARIANT.ID.eq(VARIANT_AXIS_VALUE.VARIANT_ID))
                .where(VARIANT.PRODUCT_ID.eq(productId))
                .orderBy(VARIANT_AXIS_VALUE.VARIANT_ID, VARIANT_AXIS_VALUE.POSITION)
                .fetchGroups(VARIANT_AXIS_VALUE.VARIANT_ID, VARIANT_AXIS_VALUE.VALUE);
    }
}

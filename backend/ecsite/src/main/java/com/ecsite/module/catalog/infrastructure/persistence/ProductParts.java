package com.ecsite.module.catalog.infrastructure.persistence;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.UUID;

import static com.ecsite.jooq.catalog.Tables.PRODUCT_AXIS;
import static com.ecsite.jooq.catalog.Tables.PRODUCT_CATEGORY;
import static com.ecsite.jooq.catalog.Tables.VARIANT_AXIS_VALUE;

/**
 * 商品の一部を、商品やバリアントの行と同じ問い合わせで取得する列
 *
 * <p>商品の一部ごとに問い合わせないよう、相関サブクエリの配列にする。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ProductParts {

    /**
     * バリアント軸の名前（並び順）の列を返す。
     *
     * @param productId 商品 ID の列
     * @return 軸の名前の配列の列
     */
    static Field<String[]> axes(Field<UUID> productId) {
        return DSL.array(DSL.select(PRODUCT_AXIS.NAME)
                        .from(PRODUCT_AXIS)
                        .where(PRODUCT_AXIS.PRODUCT_ID.eq(productId))
                        .orderBy(PRODUCT_AXIS.POSITION))
                .as("axes");
    }

    /**
     * 所属カテゴリの ID（ID の順）の列を返す。
     *
     * @param productId 商品 ID の列
     * @return カテゴリ ID の配列の列
     */
    static Field<UUID[]> categoryIds(Field<UUID> productId) {
        return DSL.array(DSL.select(PRODUCT_CATEGORY.CATEGORY_ID)
                        .from(PRODUCT_CATEGORY)
                        .where(PRODUCT_CATEGORY.PRODUCT_ID.eq(productId))
                        .orderBy(PRODUCT_CATEGORY.CATEGORY_ID))
                .as("category_ids");
    }

    /**
     * バリアントの軸の値（軸の並び）の列を返す。
     *
     * @param variantId バリアント ID の列
     * @return 軸の値の配列の列
     */
    static Field<String[]> axisValues(Field<UUID> variantId) {
        return DSL.array(DSL.select(VARIANT_AXIS_VALUE.VALUE)
                        .from(VARIANT_AXIS_VALUE)
                        .where(VARIANT_AXIS_VALUE.VARIANT_ID.eq(variantId))
                        .orderBy(VARIANT_AXIS_VALUE.POSITION))
                .as("axis_values");
    }
}

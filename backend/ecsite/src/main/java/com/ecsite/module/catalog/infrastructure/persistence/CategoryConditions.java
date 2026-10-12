package com.ecsite.module.catalog.infrastructure.persistence;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jooq.Condition;
import org.jooq.Record1;
import org.jooq.Select;
import org.jooq.impl.DSL;

import java.util.UUID;

import static com.ecsite.jooq.catalog.Tables.CATEGORY;
import static com.ecsite.jooq.catalog.Tables.PRODUCT;
import static com.ecsite.jooq.catalog.Tables.PRODUCT_CATEGORY;

/**
 * カテゴリによる商品の絞り込みの SQL。B 側の一覧と C 側の一覧で共通（子孫のカテゴリを含む。AC-52）。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CategoryConditions {

    /**
     * カテゴリか、その子孫のカテゴリに所属する商品であること。
     *
     * @param categoryId カテゴリ ID
     * @return 条件
     */
    static Condition productInCategory(UUID categoryId) {
        return PRODUCT.ID.in(DSL.select(PRODUCT_CATEGORY.PRODUCT_ID)
                .from(PRODUCT_CATEGORY)
                .where(PRODUCT_CATEGORY.CATEGORY_ID.in(selfAndDescendants(categoryId))));
    }

    /**
     * カテゴリと、その子孫のカテゴリの ID を選ぶ。階層は最大 3 なので、子と孫まででよい（AC-9）。
     *
     * @param categoryId カテゴリ ID
     * @return カテゴリ ID の選択
     */
    private static Select<Record1<UUID>> selfAndDescendants(UUID categoryId) {
        var child = CATEGORY.as("child");
        return DSL.select(CATEGORY.ID)
                .from(CATEGORY)
                .where(CATEGORY.ID.eq(categoryId))
                .or(CATEGORY.PARENT_ID.eq(categoryId))
                .or(CATEGORY.PARENT_ID.in(DSL.select(child.ID).from(child).where(child.PARENT_ID.eq(categoryId))));
    }
}

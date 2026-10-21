package com.ecsite;

import org.jooq.DSLContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.ecsite.jooq.tax.Tables.TAX_CATEGORY;
import static com.ecsite.jooq.tax.Tables.TAX_RATE;

/**
 * 送料に適用する税区分（10%）を、統合テストの設定（{@code ecsite.order.shipping-tax-category-id}）の ID で作る補助。<br />
 * すでにあれば何もしないので、ロールバックするテストでも、コミットするテストでも呼べる。
 */
public final class ShippingTaxCategory {

    /**
     * 統合テストの設定の、送料の税区分 ID
     */
    public static final UUID ID = UUID.fromString("00000000-0000-0000-0000-00000000f001");

    private ShippingTaxCategory() {
    }

    /**
     * 送料の税区分と、その税率（10%。開始・終了とも空白）を作る。
     *
     * @param dsl jOOQ DSL Context
     */
    public static void ensure(DSLContext dsl) {
        var now = Instant.parse("2026-01-01T00:00:00Z");
        dsl.insertInto(TAX_CATEGORY)
                .set(TAX_CATEGORY.ID, ID)
                .set(TAX_CATEGORY.NAME, "送料（テスト）")
                .set(TAX_CATEGORY.VERSION, 0L)
                .set(TAX_CATEGORY.CREATED_AT, now)
                .set(TAX_CATEGORY.UPDATED_AT, now)
                .onConflict(TAX_CATEGORY.ID)
                .doNothing()
                .execute();
        dsl.insertInto(TAX_RATE)
                .set(TAX_RATE.ID, UUID.fromString("00000000-0000-0000-0000-00000000f101"))
                .set(TAX_RATE.TAX_CATEGORY_ID, ID)
                .set(TAX_RATE.RATE_PERCENT, BigDecimal.TEN)
                // 期間の排他制約は検査を遅らせているので、主キーで重複を判定する
                .onConflict(TAX_RATE.ID)
                .doNothing()
                .execute();
    }
}

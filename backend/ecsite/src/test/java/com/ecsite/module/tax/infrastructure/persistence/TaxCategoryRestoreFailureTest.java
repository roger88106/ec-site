package com.ecsite.module.tax.infrastructure.persistence;

import com.ecsite.jooq.tax.tables.records.TaxRateRecord;
import com.ecsite.module.tax.domain.taxcategory.TaxCategoryFactory;
import com.ecsite.support.RestoreFailures;
import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

import static com.ecsite.jooq.tax.Tables.TAX_CATEGORY;
import static com.ecsite.jooq.tax.Tables.TAX_RATE;
import static com.ecsite.support.RestoreFailures.RECORDS;
import static com.ecsite.support.RestoreFailures.assertValueIsHidden;

/**
 * 税区分を DB の値から戻すときの失敗の単体テスト
 *
 * <p>例外の出力に DB の値が出ないことだけを確認する。値を正しく戻すことは、Repository の統合テストが確認する。
 */
class TaxCategoryRestoreFailureTest {

    // ============================== 定数 ==============================

    /**
     * 率の範囲の外の値（%）
     */
    private static final BigDecimal OUT_OF_RANGE_PERCENT = new BigDecimal("123.45");

    /**
     * 最後の税率にある適用終了日時（最後の税率は終了が空白でなければならない）
     */
    private static final Instant EFFECTIVE_TO = Instant.parse("2031-02-03T04:05:06Z");

    // ============================== テスト ==============================

    @Test
    @DisplayName("税率の率が範囲の外のとき、税区分の取得で例外の出力に値が出ない")
    void hidesRatePercentOfCategory() {
        // 操作と確認：率を範囲の外にした行を読む
        var repository = repositoryWith(rate -> rate.setRatePercent(OUT_OF_RANGE_PERCENT));
        assertValueIsHidden(() -> repository.findById(UUID.randomUUID()), OUT_OF_RANGE_PERCENT.toPlainString());
    }

    @Test
    @DisplayName("最後の税率に終了があるとき、税区分の取得で例外の出力に値が出ない")
    void hidesClosedLastPeriod() {
        // 操作と確認：終了のある 1 件だけの税率を読む
        var repository = repositoryWith(rate -> rate.setEffectiveTo(EFFECTIVE_TO));
        assertValueIsHidden(() -> repository.findById(UUID.randomUUID()), EFFECTIVE_TO.toString());
    }

    @Test
    @DisplayName("税率の率が範囲の外のとき、読み取りのモデルの取得で例外の出力に値が出ない")
    void hidesRatePercentOfView() {
        // 操作と確認：率を範囲の外にした行を読む
        var query = new JooqTaxCategoryQueryRepository(dslWith(rate -> rate.setRatePercent(OUT_OF_RANGE_PERCENT)));
        assertValueIsHidden(query::findAll, OUT_OF_RANGE_PERCENT.toPlainString());
    }

    // ============================== 確認の補助 ==============================

    /**
     * 税率の行を壊して、税区分のリポジトリを作る。
     *
     * @param breaking 税率の行を壊す処理
     * @return リポジトリ
     */
    private static JooqTaxCategoryRepository repositoryWith(Consumer<TaxRateRecord> breaking) {
        return new JooqTaxCategoryRepository(dslWith(breaking), new TaxCategoryFactory(), Clock.systemUTC());
    }

    /**
     * 税区分の行と、壊した税率の行を返す jOOQ の文脈を作る。
     *
     * @param breaking 税率の行を壊す処理
     * @return jOOQ の文脈
     */
    private static DSLContext dslWith(Consumer<TaxRateRecord> breaking) {
        var categoryId = UUID.randomUUID();
        var category = RECORDS.newRecord(TAX_CATEGORY);
        category.setId(categoryId);
        category.setName("標準");
        category.setVersion(0L);
        var rate = RECORDS.newRecord(TAX_RATE);
        rate.setId(UUID.randomUUID());
        rate.setTaxCategoryId(categoryId);
        rate.setRatePercent(new BigDecimal("10"));
        breaking.accept(rate);
        return new RestoreFailures().with("tax_category", category).with("tax_rate", rate).dsl();
    }
}

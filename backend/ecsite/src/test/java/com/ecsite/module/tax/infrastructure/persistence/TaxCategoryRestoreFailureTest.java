package com.ecsite.module.tax.infrastructure.persistence;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.model.id.TaxCategoryId;
import com.ecsite.jooq.tax.tables.records.TaxRateRecord;
import com.ecsite.module.tax.domain.taxcategory.TaxCategoryFactory;
import com.ecsite.support.RestoreFailures;
import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static com.ecsite.jooq.tax.Tables.TAX_CATEGORY;
import static com.ecsite.jooq.tax.Tables.TAX_RATE;
import static com.ecsite.support.RestoreFailures.RECORDS;
import static com.ecsite.support.RestoreFailures.assertDatabaseFailurePropagates;
import static com.ecsite.support.RestoreFailures.assertValueIsHidden;

/**
 * 税区分を DB の値から戻すときの失敗の単体テスト
 *
 * <p>例外の出力に DB の値が出ないことだけを確認する。値を正しく戻すことは、Repository の統合テストが確認する。
 */
class TaxCategoryRestoreFailureTest {

    /**
     * 例外のメッセージに入ると分かる値
     *
     * <p>税区分の名称に入れる。
     */
    private final String value = RestoreFailures.value();

    // ============================== 定数 ==============================

    /**
     * 率の範囲の外の値（%）
     */
    private static final BigDecimal OUT_OF_RANGE_PERCENT = new BigDecimal("123.45");

    /**
     * 期間の境目の早い方
     */
    private static final Instant EARLIER = Instant.parse("2027-03-31T15:00:00Z");

    /**
     * 期間の境目の遅い方
     */
    private static final Instant LATER = Instant.parse("2028-03-31T15:00:00Z");

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
        assertValueIsHidden(() -> repository.findById(new TaxCategoryId(UUID.randomUUID())), OUT_OF_RANGE_PERCENT.toPlainString(), BusinessException.class);
    }

    @Test
    @DisplayName("最後の税率に終了があるとき、税区分の取得で例外の出力に値が出ない")
    void hidesClosedLastPeriod() {
        // 操作と確認：終了のある 1 件だけの税率を読む
        var repository = repositoryWith(rate -> rate.setEffectiveTo(EFFECTIVE_TO));
        assertValueIsHidden(() -> repository.findById(new TaxCategoryId(UUID.randomUUID())), EFFECTIVE_TO.toString(), BusinessException.class);
    }

    @Test
    @DisplayName("税率の期間が重なるとき、税区分の取得で例外の出力に値が出ない")
    void hidesOverlappingPeriods() {
        // 準備：1 件目の終了が 2 件目の開始より後の 2 件の税率と、名称に目印の値がある税区分
        var first = rateRecord("8", null, LATER);
        var second = rateRecord("10", EARLIER, null);
        var repository = new JooqTaxCategoryRepository(dslWith(value, List.of(first, second)), new TaxCategoryFactory(), Clock.systemUTC());

        // 操作と確認：復元の失敗になり、例外の出力に値が出ない
        assertValueIsHidden(() -> repository.findById(new TaxCategoryId(UUID.randomUUID())), value, BusinessException.class);
    }

    @Test
    @DisplayName("税率の期間が途切れるとき、税区分の取得で例外の出力に値が出ない")
    void hidesPeriodGap() {
        // 準備：1 件目の終了と 2 件目の開始の間が空く 2 件の税率と、名称に目印の値がある税区分
        var first = rateRecord("8", null, EARLIER);
        var second = rateRecord("10", LATER, null);
        var repository = new JooqTaxCategoryRepository(dslWith(value, List.of(first, second)), new TaxCategoryFactory(), Clock.systemUTC());

        // 操作と確認：復元の失敗になり、例外の出力に値が出ない
        assertValueIsHidden(() -> repository.findById(new TaxCategoryId(UUID.randomUUID())), value, BusinessException.class);
    }

    @Test
    @DisplayName("税率の表を読む DB の失敗は、復元のエラーに変わらずそのまま出る")
    void propagatesRateReadFailure() {
        // 準備：税率の表を読むと失敗する DB
        var category = RECORDS.newRecord(TAX_CATEGORY);
        category.setId(UUID.randomUUID());
        category.setName("標準");
        category.setVersion(0L);
        var dsl = new RestoreFailures().with("tax_category", category).failingOn("tax_rate").dsl();
        var repository = new JooqTaxCategoryRepository(dsl, new TaxCategoryFactory(), Clock.systemUTC());

        // 操作と確認：DB の失敗のまま出る
        assertDatabaseFailurePropagates(() -> repository.findById(new TaxCategoryId(UUID.randomUUID())));
    }

    @Test
    @DisplayName("税率の率が範囲の外のとき、読み取りのモデルの取得で例外の出力に値が出ない")
    void hidesRatePercentOfView() {
        // 操作と確認：率を範囲の外にした行を読む
        var query = new JooqTaxCategoryQueryRepository(dslWith(rate -> rate.setRatePercent(OUT_OF_RANGE_PERCENT)));
        assertValueIsHidden(query::findAll, OUT_OF_RANGE_PERCENT.toPlainString(), BusinessException.class);
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
        var rate = rateRecord("10", null, null);
        breaking.accept(rate);
        return dslWith("標準", List.of(rate));
    }

    /**
     * 税区分の行と、複数の税率の行を返す jOOQ の文脈を作る。
     *
     * @param name  税区分の名称
     * @param rates 税率の行
     * @return jOOQ の文脈
     */
    private static DSLContext dslWith(String name, List<TaxRateRecord> rates) {
        var category = RECORDS.newRecord(TAX_CATEGORY);
        category.setId(UUID.randomUUID());
        category.setName(name);
        category.setVersion(0L);
        return new RestoreFailures().with("tax_category", category).withRows("tax_rate", List.copyOf(rates)).dsl();
    }

    /**
     * 税率の行を作る。
     *
     * @param percent 率（%）
     * @param from    適用開始日時。なければ null
     * @param to      適用終了日時。なければ null
     * @return 税率の行
     */
    private static TaxRateRecord rateRecord(String percent, @Nullable Instant from, @Nullable Instant to) {
        var rate = RECORDS.newRecord(TAX_RATE);
        rate.setId(UUID.randomUUID());
        rate.setTaxCategoryId(UUID.randomUUID());
        rate.setRatePercent(new BigDecimal(percent));
        rate.setEffectiveFrom(from);
        rate.setEffectiveTo(to);
        return rate;
    }
}

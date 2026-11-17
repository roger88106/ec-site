package com.ecsite.module.tax.infrastructure.persistence;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.persistence.ConstraintViolationTranslator;
import com.ecsite.jooq.tax.Keys;
import com.ecsite.jooq.tax.tables.records.TaxCategoryRecord;
import com.ecsite.jooq.tax.tables.records.TaxRateRecord;
import com.ecsite.module.tax.domain.TaxErrorCode;
import com.ecsite.module.tax.domain.taxcategory.TaxCategory;
import com.ecsite.module.tax.domain.taxcategory.TaxCategoryFactory;
import com.ecsite.module.tax.domain.taxcategory.TaxRate;
import com.ecsite.module.tax.domain.taxcategory.port.TaxCategoryRepository;
import com.ecsite.module.tax.domain.taxcategory.vo.EffectivePeriod;
import com.ecsite.module.tax.domain.taxcategory.vo.RatePercent;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Name;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.ecsite.jooq.tax.Tables.TAX_CATEGORY;
import static com.ecsite.jooq.tax.Tables.TAX_RATE;

/**
 * 税区分のリポジトリ
 */
@Repository
@RequiredArgsConstructor
class JooqTaxCategoryRepository implements TaxCategoryRepository {

    /**
     * 適用期間の重複を防ぐ排他制約（検査を遅らせる設定）
     *
     * <p>jOOQ は排他制約のコードを生成しないので、名前で指す。
     */
    static final Name PERIOD_NO_OVERLAP = DSL.name("tax", "tax_rate_period_no_overlap");

    /**
     * 制約名 → 業務のエラー
     */
    private static final ConstraintViolationTranslator CONSTRAINTS = ConstraintViolationTranslator.of(Map.of(
            Keys.TAX_CATEGORY_NAME_KEY.getName(), () -> BusinessException.of(TaxErrorCode.TAX_CATEGORY_NAME_ALREADY_EXISTS),
            PERIOD_NO_OVERLAP.last(), () -> BusinessException.of(TaxErrorCode.TAX_RATE_PERIODS_OVERLAP)));

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

    /**
     * 税区分ファクトリ
     */
    private final TaxCategoryFactory factory;

    /**
     * 時計（登録・更新日時）
     */
    private final Clock clock;

    /**
     * 税区分を取得する。
     *
     * @param id 税区分 ID
     * @return 税区分
     */
    @Override
    public Optional<TaxCategory> findById(UUID id) {
        return dsl.selectFrom(TAX_CATEGORY)
                .where(TAX_CATEGORY.ID.eq(id))
                .fetchOptional()
                .map(this::toTaxCategory);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void add(TaxCategory taxCategory) {
        var snapshot = taxCategory.toSnapshot();
        var now = clock.instant();

        CONSTRAINTS.run(() -> {
            // 税区分の登録
            dsl.insertInto(TAX_CATEGORY)
                    .set(TAX_CATEGORY.ID, snapshot.id())
                    .set(TAX_CATEGORY.NAME, snapshot.name())
                    .set(TAX_CATEGORY.VERSION, snapshot.version())
                    .set(TAX_CATEGORY.CREATED_AT, now)
                    .set(TAX_CATEGORY.UPDATED_AT, now)
                    .execute();

            // 税率の登録
            writeRates(snapshot);
        });
    }

    /**
     * {@inheritDoc}
     *
     * <p>税率は差分で更新する：なくなった税率を削除し、残りを追加または更新する。
     */
    @Override
    public void save(TaxCategory taxCategory) {
        var snapshot = taxCategory.toSnapshot();

        CONSTRAINTS.run(() -> {
            // 税区分の更新 ※バージョンが一致しなければ、取得後に他で更新されている。
            // 税区分の行を先にロックするので、同じ税区分の税率の書き換えは直列になる
            int affected = dsl.update(TAX_CATEGORY)
                    .set(TAX_CATEGORY.NAME, snapshot.name())
                    .set(TAX_CATEGORY.VERSION, snapshot.version() + 1)
                    .set(TAX_CATEGORY.UPDATED_AT, clock.instant())
                    .where(TAX_CATEGORY.ID.eq(snapshot.id()))
                    .and(TAX_CATEGORY.VERSION.eq(snapshot.version()))
                    .execute();
            if (affected == 0) {
                throw BusinessException.of(CommonErrorCode.CONCURRENT_MODIFICATION);
            }

            // なくなった税率の削除
            dsl.deleteFrom(TAX_RATE)
                    .where(TAX_RATE.TAX_CATEGORY_ID.eq(snapshot.id()))
                    .and(TAX_RATE.ID.notIn(snapshot.rates().stream().map(TaxRate::id).toList()))
                    .execute();

            // 税率の追加・更新
            writeRates(snapshot);
        });
    }

// ============================== プライベートメソッド ==============================

    /**
     * 税区分のレコードと、その税率から、税区分を復元する。
     */
    private TaxCategory toTaxCategory(TaxCategoryRecord category) {
        // 税率の取得
        var rates = dsl.selectFrom(TAX_RATE)
                .where(TAX_RATE.TAX_CATEGORY_ID.eq(category.getId()))
                .fetch(JooqTaxCategoryRepository::toTaxRate);

        // スナップショットからドメインに変換する
        var snapshot = new TaxCategory.Snapshot(category.getId(), category.getName(), rates, category.getVersion());
        return factory.restore(snapshot);
    }

    /**
     * 税率を追加または更新し、遅らせていた排他制約を検査させる。
     *
     * @param snapshot 税区分のスナップショット
     */
    private void writeRates(TaxCategory.Snapshot snapshot) {
        upsertRates(snapshot);
        // 途中で一時的に期間が重なってもよいので、排他制約は最後に検査する
        checkDeferredConstraints();
    }

    /**
     * 税率を 1 つの文で追加する。すでにあるものは更新する。
     *
     * @param snapshot 税区分のスナップショット
     */
    private void upsertRates(TaxCategory.Snapshot snapshot) {
        if (snapshot.rates().isEmpty()) {
            return;
        }
        var insert = dsl.insertInto(TAX_RATE, TAX_RATE.ID, TAX_RATE.TAX_CATEGORY_ID, TAX_RATE.RATE_PERCENT,
                TAX_RATE.EFFECTIVE_FROM, TAX_RATE.EFFECTIVE_TO);
        for (var rate : snapshot.rates()) {
            var period = rate.period();
            insert = insert.values(rate.id(), snapshot.id(), rate.ratePercent().value(), period.effectiveFrom(),
                    period.effectiveTo());
        }
        insert.onConflict(TAX_RATE.ID)
                .doUpdate()
                .set(TAX_RATE.RATE_PERCENT, DSL.excluded(TAX_RATE.RATE_PERCENT))
                .set(TAX_RATE.EFFECTIVE_FROM, DSL.excluded(TAX_RATE.EFFECTIVE_FROM))
                .set(TAX_RATE.EFFECTIVE_TO, DSL.excluded(TAX_RATE.EFFECTIVE_TO))
                .execute();
    }

    /**
     * 遅らせていた排他制約（適用期間の重複）を、この時点で検査させる。
     */
    private void checkDeferredConstraints() {
        // コミット時まで遅らせると、違反がリポジトリの外で起き、業務のエラーに変換できないため、ここで検査する
        dsl.execute("SET CONSTRAINTS {0} IMMEDIATE", PERIOD_NO_OVERLAP);
        // 遅らせる設定に戻し、同じトランザクションの後続の書き込みでも途中の重なりを許す
        dsl.execute("SET CONSTRAINTS {0} DEFERRED", PERIOD_NO_OVERLAP);
    }

    /**
     * レコードから税率に変換する。
     *
     * @param record 税率のレコード
     * @return 税率
     */
    private static TaxRate toTaxRate(TaxRateRecord record) {
        return new TaxRate(
                record.getId(),
                RatePercent.of(record.getRatePercent()),
                EffectivePeriod.of(record.getEffectiveFrom(), record.getEffectiveTo()));
    }
}

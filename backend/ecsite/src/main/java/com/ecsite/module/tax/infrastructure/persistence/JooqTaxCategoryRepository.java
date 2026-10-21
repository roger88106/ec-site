package com.ecsite.module.tax.infrastructure.persistence;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.persistence.ConstraintViolationTranslator;
import com.ecsite.jooq.tax.Keys;
import com.ecsite.jooq.tax.tables.records.TaxRateRecord;
import com.ecsite.module.tax.domain.taxcategory.TaxCategory;
import com.ecsite.module.tax.domain.taxcategory.TaxCategoryFactory;
import com.ecsite.module.tax.domain.taxcategory.TaxErrorCode;
import com.ecsite.module.tax.domain.taxcategory.TaxRate;
import com.ecsite.module.tax.domain.taxcategory.port.TaxCategoryRepository;
import com.ecsite.module.tax.domain.taxcategory.vo.EffectivePeriod;
import com.ecsite.module.tax.domain.taxcategory.vo.RatePercent;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Name;
import org.jooq.impl.DSL;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
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
     * 適用期間の重複を防ぐ排他制約（検査を遅らせる設定）。<br />
     * jOOQ は排他制約のコードを生成せず、SET CONSTRAINTS の DSL もないため、名前で指す。
     * マイグレーションとの一致は統合テストで確認する（JooqTaxCategoryRepositoryTest）。
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
     * Spring イベントパブリッシャー
     */
    private final ApplicationEventPublisher eventPublisher;

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
                // スナップショットに変換する
                .map(category -> new TaxCategory.Snapshot(
                        category.getId(),
                        category.getName(),
                        dsl.selectFrom(TAX_RATE)
                                .where(TAX_RATE.TAX_CATEGORY_ID.eq(id))
                                .fetch(JooqTaxCategoryRepository::toTaxRate),
                        category.getVersion()))
                // スナップショットからドメインに変換する
                .map(factory::restore);
    }

    /**
     * 税区分を登録する。
     *
     * @param taxCategory 税区分
     */
    @Override
    public void add(TaxCategory taxCategory) {
        var snapshot = taxCategory.toSnapshot();
        var now = clock.instant();

        try {
            // 税区分の登録
            dsl.insertInto(TAX_CATEGORY)
                    .set(TAX_CATEGORY.ID, snapshot.id())
                    .set(TAX_CATEGORY.NAME, snapshot.name())
                    .set(TAX_CATEGORY.VERSION, snapshot.version())
                    .set(TAX_CATEGORY.CREATED_AT, now)
                    .set(TAX_CATEGORY.UPDATED_AT, now)
                    .execute();

            // 税率の登録
            snapshot.rates().forEach(rate -> upsertRate(snapshot.id(), rate));
            checkDeferredConstraints();
        } catch (DataAccessException | org.jooq.exception.DataAccessException e) {
            throw CONSTRAINTS.translate(e);
        }

        // イベント発行
        taxCategory.pullEvents().forEach(eventPublisher::publishEvent);
    }

    /**
     * 税区分を更新する。<br />
     * 税率は差分で更新する：なくなった税率を削除し、残りを追加または更新する。
     *
     * @param taxCategory 税区分
     */
    @Override
    public void save(TaxCategory taxCategory) {
        var snapshot = taxCategory.toSnapshot();

        try {
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

            // 税率の追加・更新 ※途中で一時的に期間が重なってもよい（排他制約の検査は最後に行う）
            snapshot.rates().forEach(rate -> upsertRate(snapshot.id(), rate));
            checkDeferredConstraints();
        } catch (DataAccessException | org.jooq.exception.DataAccessException e) {
            throw CONSTRAINTS.translate(e);
        }

        // イベント発行
        taxCategory.pullEvents().forEach(eventPublisher::publishEvent);
    }

// ============================== プライベートメソッド ==============================

    /**
     * 税率を追加する。すでにあれば更新する。
     *
     * @param taxCategoryId 税区分 ID
     * @param rate          税率
     */
    private void upsertRate(UUID taxCategoryId, TaxRate rate) {
        var period = rate.period();
        dsl.insertInto(TAX_RATE)
                .set(TAX_RATE.ID, rate.id())
                .set(TAX_RATE.TAX_CATEGORY_ID, taxCategoryId)
                .set(TAX_RATE.RATE_PERCENT, rate.ratePercent().value())
                .set(TAX_RATE.EFFECTIVE_FROM, period.effectiveFrom())
                .set(TAX_RATE.EFFECTIVE_TO, period.effectiveTo())
                .onConflict(TAX_RATE.ID)
                .doUpdate()
                .set(TAX_RATE.RATE_PERCENT, rate.ratePercent().value())
                .set(TAX_RATE.EFFECTIVE_FROM, period.effectiveFrom())
                .set(TAX_RATE.EFFECTIVE_TO, period.effectiveTo())
                .execute();
    }

    /**
     * 遅らせていた排他制約（適用期間の重複）を、この時点で検査させる。<br />
     * コミット時まで遅らせると、違反がリポジトリの外で起き、業務のエラーに変換できないため（docs/backend/modules/tax.md）。
     * 検査の後は遅らせる設定に戻し、同じトランザクションの後続の書き込みでも途中の重なりを許す。
     */
    private void checkDeferredConstraints() {
        dsl.execute("SET CONSTRAINTS {0} IMMEDIATE", PERIOD_NO_OVERLAP);
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

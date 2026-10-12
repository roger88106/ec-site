package com.ecsite.module.tax.infrastructure.persistence;

import com.ecsite.IntegrationTest;
import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.module.tax.domain.taxcategory.TaxCategory;
import com.ecsite.module.tax.domain.taxcategory.TaxCategoryFactory;
import com.ecsite.module.tax.domain.taxcategory.TaxErrorCode;
import com.ecsite.module.tax.domain.taxcategory.TaxRate;
import com.ecsite.module.tax.domain.taxcategory.TaxRateDraft;
import com.ecsite.module.tax.domain.taxcategory.port.TaxCategoryRepository;
import com.ecsite.module.tax.domain.taxcategory.vo.EffectivePeriod;
import com.ecsite.module.tax.domain.taxcategory.vo.RatePercent;
import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 税区分のリポジトリの統合テスト（T106）。<br />
 * テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class JooqTaxCategoryRepositoryTest {

    /**
     * 現在：2026-10-08 00:00（日本時間）
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 2027-04-01 00:00（日本時間）
     */
    private static final Instant APR_2027 = Instant.parse("2027-03-31T15:00:00Z");

    /**
     * 2028-04-01 00:00（日本時間）
     */
    private static final Instant APR_2028 = Instant.parse("2028-03-31T15:00:00Z");

    @Autowired
    TaxCategoryRepository repository;

    @Autowired
    TaxCategoryFactory factory;

    @Autowired
    DSLContext dsl;

    @Test
    @DisplayName("登録した税区分を再構築できる")
    void addsAndRestores() {
        var category = factory.create("標準");

        repository.add(category);

        var restored = repository.findById(category.id()).orElseThrow();
        assertThat(restored.name()).isEqualTo("標準");
        assertThat(restored.rates()).isEmpty();
        assertThat(restored.version()).isZero();
    }

    @Test
    @DisplayName("存在しない税区分は見つからない")
    void returnsEmptyForUnknownId() {
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("AC-1 名称が重複する税区分は登録できない")
    void rejectsDuplicatedName() {
        repository.add(factory.create("標準"));

        assertBusinessError(() -> repository.add(factory.create("標準")), TaxErrorCode.TAX_CATEGORY_NAME_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("税率の一覧を保存し、再構築できる。保存するとバージョンが上がる")
    void savesRates() {
        var category = factory.create("標準");
        repository.add(category);

        category.replaceRates(List.of(
                newDraft("10", null, APR_2027),
                newDraft("8", APR_2027, APR_2028),
                newDraft("10", APR_2028, null)), NOW);
        repository.save(category);

        var restored = repository.findById(category.id()).orElseThrow();
        assertThat(restored.rates()).containsExactlyElementsOf(category.rates());
        assertThat(restored.version()).isEqualTo(1);
    }

    @Test
    @DisplayName("置き換えで、残す税率は更新し、なくなった税率は削除し、新しい税率は追加する")
    void replacesRates() {
        var category = saved(
                newDraft("10", null, APR_2027),
                newDraft("8", APR_2027, null));
        var base = category.rates().getFirst();

        category.replaceRates(List.of(
                draft(base.id(), "10", null, APR_2028),
                newDraft("12", APR_2028, null)), NOW);
        repository.save(category);

        var restored = repository.findById(category.id()).orElseThrow();
        assertThat(restored.rates()).containsExactlyElementsOf(category.rates());
        assertThat(restored.rates()).extracting(TaxRate::id).first().isEqualTo(base.id());
        assertThat(restored.version()).isEqualTo(2);
    }

    @Test
    @DisplayName("置き換えの途中で一時的に期間が重なっても、結果に重なりがなければ成功する")
    void allowsTemporaryOverlap() {
        var category = saved(
                newDraft("10", null, APR_2027),
                newDraft("8", APR_2027, null));
        var first = category.rates().getFirst();
        var second = category.rates().get(1);

        // 1 件目の終了を先に延ばすと、2 件目を書き換えるまでの間、期間が重なる
        category.replaceRates(List.of(
                draft(first.id(), "10", null, APR_2028),
                draft(second.id(), "8", APR_2028, null)), NOW);
        repository.save(category);

        var restored = repository.findById(category.id()).orElseThrow();
        assertThat(restored.rates()).containsExactlyElementsOf(category.rates());
    }

    @Test
    @DisplayName("取得後に他で更新されていたら、バージョン不一致で保存できない")
    void detectsVersionMismatch() {
        var saved = saved(newDraft("10", null, null));
        var mine = repository.findById(saved.id()).orElseThrow();
        var theirs = repository.findById(saved.id()).orElseThrow();
        var rateId = theirs.rates().getFirst().id();

        theirs.replaceRates(List.of(draft(rateId, "10", null, APR_2027), newDraft("8", APR_2027, null)), NOW);
        repository.save(theirs);

        mine.replaceRates(List.of(draft(rateId, "10", null, APR_2028), newDraft("8", APR_2028, null)), NOW);
        assertBusinessError(() -> repository.save(mine), CommonErrorCode.CONCURRENT_MODIFICATION);
    }

    @Test
    @DisplayName("期間が重なる税率は、排他制約で拒否され、業務のエラーになる（500 にならない）")
    void rejectsOverlapByExclusionConstraint() {
        var category = factory.create("標準");
        repository.add(category);

        // ドメインの検証を通らない状態（期間の重複）を、スナップショットから直接作る
        var overlapping = factory.restore(new TaxCategory.Snapshot(category.id(), category.name(), List.of(
                rate("10", null, APR_2028),
                rate("8", APR_2027, null)), category.version()));

        assertBusinessError(() -> repository.save(overlapping), TaxErrorCode.TAX_RATE_PERIODS_OVERLAP);
    }

    @Test
    @DisplayName("リポジトリが名前で指す排他制約が、マイグレーションに、検査を遅らせる設定で存在する")
    void periodNoOverlapConstraintExists() {
        // jOOQ は排他制約のコードを生成しないので、名前のずれをコンパイルで検出できない。ここで確かめる
        var constraint = dsl.fetchOne("""
                        SELECT c.contype, c.condeferrable, c.condeferred
                        FROM pg_constraint c
                        JOIN pg_namespace n ON n.oid = c.connamespace
                        WHERE n.nspname = {0} AND c.conname = {1}""",
                JooqTaxCategoryRepository.PERIOD_NO_OVERLAP.first(),
                JooqTaxCategoryRepository.PERIOD_NO_OVERLAP.last());

        assertThat(constraint).as("制約 %s がない", JooqTaxCategoryRepository.PERIOD_NO_OVERLAP).isNotNull();
        // x：排他制約
        assertThat(constraint.get("contype", String.class)).isEqualTo("x");
        assertThat(constraint.get("condeferrable", Boolean.class)).isTrue();
        assertThat(constraint.get("condeferred", Boolean.class)).isTrue();
    }

    // ============================== 補助 ==============================

    /**
     * 税率を持つ税区分を保存し、保存後の状態を返す。
     */
    private TaxCategory saved(TaxRateDraft... drafts) {
        var category = factory.create("標準");
        repository.add(category);
        category.replaceRates(List.of(drafts), NOW);
        repository.save(category);
        return repository.findById(category.id()).orElseThrow();
    }

    /**
     * 税率を作る（ID は新しく振る）。期間の端の null は無限
     */
    private static TaxRate rate(String percent, Instant from, Instant to) {
        return new TaxRate(UUID.randomUUID(), RatePercent.of(new BigDecimal(percent)), EffectivePeriod.of(from, to));
    }

    /**
     * 新しい税率（ID なし）の置き換えの要素を作る
     */
    private static TaxRateDraft newDraft(String percent, Instant from, Instant to) {
        return draft(null, percent, from, to);
    }

    /**
     * 置き換えの要素を作る。既存の税率を残すなら、その ID を渡す
     */
    private static TaxRateDraft draft(UUID id, String percent, Instant from, Instant to) {
        return new TaxRateDraft(id, RatePercent.of(new BigDecimal(percent)), EffectivePeriod.of(from, to));
    }
}

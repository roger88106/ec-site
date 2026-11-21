package com.ecsite.module.tax.infrastructure.persistence;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.module.tax.domain.TaxErrorCode;
import com.ecsite.module.tax.domain.taxcategory.TaxCategory;
import com.ecsite.module.tax.domain.taxcategory.TaxCategoryFactory;
import com.ecsite.module.tax.domain.taxcategory.TaxRate;
import com.ecsite.module.tax.domain.taxcategory.port.TaxCategoryRepository;
import com.ecsite.module.tax.domain.taxcategory.vo.TaxRateDraft;
import com.ecsite.support.config.IntegrationTest;
import org.assertj.core.groups.Tuple;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static com.ecsite.module.tax.domain.taxcategory.TestTaxRates.draft;
import static com.ecsite.module.tax.domain.taxcategory.TestTaxRates.newDraft;
import static com.ecsite.module.tax.domain.taxcategory.TestTaxRates.rate;
import static com.ecsite.support.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * 税区分のリポジトリの統合テスト
 *
 * <p>テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class JooqTaxCategoryRepositoryTest {

    // ============================== 定数 ==============================

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 2027 年 4 月の初め
     *
     * <p>日本時間の 2027-04-01 00:00。
     */
    private static final Instant APR_2027 = Instant.parse("2027-03-31T15:00:00Z");

    /**
     * 2028 年 4 月の初め
     *
     * <p>日本時間の 2028-04-01 00:00。
     */
    private static final Instant APR_2028 = Instant.parse("2028-03-31T15:00:00Z");

    /**
     * 税区分の名称
     */
    private static final String NAME = "標準";

    // ============================== Bean ==============================

    @Autowired
    TaxCategoryRepository repository;

    @Autowired
    TaxCategoryFactory factory;

    @Autowired
    DSLContext dsl;

    // ============================== テスト ==============================

    @Nested
    @DisplayName("登録と再構築")
    class AddAndRestore {

        @Test
        @DisplayName("登録した税区分を再構築できる")
        void addsAndRestores() {
            // 準備：税率のない税区分
            var category = factory.create(UUID.randomUUID(), NAME);

            // 操作：登録して再取得する
            repository.add(category);
            var restored = repository.findById(category.id()).orElseThrow();

            // 確認：名称、税率なし、バージョン 0 のまま再構築される
            assertThat(restored.name()).isEqualTo(NAME);
            assertThat(restored.rates()).isEmpty();
            assertThat(restored.version()).isZero();
        }

        @Test
        @DisplayName("存在しない税区分は見つからない")
        void returnsEmptyForUnknownId() {
            // 操作と確認：登録していない ID は空
            assertThat(repository.findById(UUID.randomUUID())).isEmpty();
        }

        @Test
        @DisplayName("AC-1 名称が重複する税区分は登録できない")
        void rejectsDuplicatedName() {
            // 準備：同じ名称の税区分を登録済み
            repository.add(factory.create(UUID.randomUUID(), NAME));

            // 操作と確認：業務のエラー TAX_CATEGORY_NAME_ALREADY_EXISTS で拒否される
            assertBusinessError(() -> repository.add(factory.create(UUID.randomUUID(), NAME)), TaxErrorCode.TAX_CATEGORY_NAME_ALREADY_EXISTS);
        }
    }

    @Nested
    @DisplayName("税率の一覧の保存")
    class SaveRates {

        @Test
        @DisplayName("税率の一覧を保存し、再構築できる")
        void savesRates() {
            // 準備：登録済みの税区分の税率の一覧を、連続する 3 件に置き換える
            var category = addedWithContinuousRates();
            var ids = idsOf(category);

            // 操作：保存して再取得する
            var restored = saveAndReload(category);

            // 確認：保存した税率の一覧が、同じ順で再構築される
            assertRates(restored,
                    draft(ids.get(0), "10", null, APR_2027),
                    draft(ids.get(1), "8", APR_2027, APR_2028),
                    draft(ids.get(2), "10", APR_2028, null));
        }

        @Test
        @DisplayName("置き換えで、残す税率は更新し、なくなった税率は削除し、新しい税率は追加する")
        void replacesRates() {
            // 準備：10%（〜2027-04-01）と 8%（2027-04-01〜）を保存済みの税区分
            var category = savedWithTwoRates();
            var base = category.rates().getFirst();

            // 操作：1 件目の終了を延ばし、2 件目を外して 12% を加え、保存して再取得する
            category.replaceRates(UUID::randomUUID, extendFirstAndReplaceSecond(base.id()), NOW);
            var added = idsOf(category).get(1);
            var restored = saveAndReload(category);

            // 確認：置き換え後の一覧が再構築され、残した税率は ID が変わらない
            assertRates(restored,
                    draft(base.id(), "10", null, APR_2028),
                    draft(added, "12", APR_2028, null));
        }

        @Test
        @DisplayName("置き換えの途中で一時的に期間が重なっても、結果に重なりがなければ成功する")
        void allowsTemporaryOverlap() {
            // 準備：10%（〜2027-04-01）と 8%（2027-04-01〜）を保存済みの税区分
            var category = savedWithTwoRates();
            var first = category.rates().getFirst();
            var second = category.rates().get(1);

            // 操作：1 件目の終了を先に延ばす（2 件目を書き換えるまでの間、期間が重なる）一覧を保存して再取得する
            category.replaceRates(UUID::randomUUID, List.of(
                    draft(first.id(), "10", null, APR_2028),
                    draft(second.id(), "8", APR_2028, null)), NOW);
            var restored = saveAndReload(category);

            // 確認：置き換え後の一覧が再構築される
            assertRates(restored,
                    draft(first.id(), "10", null, APR_2028),
                    draft(second.id(), "8", APR_2028, null));
        }
    }

    @Nested
    @DisplayName("バージョン")
    class Version {

        @Test
        @DisplayName("税率の一覧を保存すると、バージョンが上がる")
        void incrementsVersionOnSave() {
            // 準備：登録済みの税区分の税率の一覧を、連続する 3 件に置き換える
            var category = addedWithContinuousRates();

            // 操作：保存して再取得する
            var restored = saveAndReload(category);

            // 確認：0 から 1 に上がる
            assertThat(restored.version()).isEqualTo(1);
        }

        @Test
        @DisplayName("置き換えを保存するたびに、バージョンが上がる")
        void incrementsVersionOnEachSave() {
            // 準備：税率の一覧を 1 回保存した税区分
            var category = savedWithTwoRates();

            // 操作：もう一度置き換えを保存して再取得する
            category.replaceRates(UUID::randomUUID, extendFirstAndReplaceSecond(category.rates().getFirst().id()), NOW);
            var restored = saveAndReload(category);

            // 確認：1 から 2 に上がる
            assertThat(restored.version()).isEqualTo(2);
        }

        @Test
        @DisplayName("取得後に他で更新されていたら、バージョン不一致で保存できない")
        void detectsVersionMismatch() {
            // 準備：同じ税区分を 2 回取得し、先に一方を保存する
            var saved = saved(newDraft("10", null, null));
            var mine = repository.findById(saved.id()).orElseThrow();
            var theirs = repository.findById(saved.id()).orElseThrow();
            var rateId = theirs.rates().getFirst().id();
            theirs.replaceRates(UUID::randomUUID, List.of(draft(rateId, "10", null, APR_2027), newDraft("8", APR_2027, null)), NOW);
            repository.save(theirs);

            // 準備：もう一方を、取得したときのバージョンのまま置き換える
            mine.replaceRates(UUID::randomUUID, List.of(draft(rateId, "10", null, APR_2028), newDraft("8", APR_2028, null)), NOW);

            // 操作と確認：保存すると、業務のエラー CONCURRENT_MODIFICATION で拒否される
            assertBusinessError(() -> repository.save(mine), CommonErrorCode.CONCURRENT_MODIFICATION);
        }
    }

    @Nested
    @DisplayName("期間の重複の排他制約")
    class PeriodNoOverlap {

        @Test
        @DisplayName("期間が重なる税率は、排他制約で拒否され、業務のエラーになる（500 にならない）")
        void rejectsOverlapByExclusionConstraint() {
            // 準備：ドメインの検証を通らない状態（期間の重複）を、スナップショットを差し替えて作る（税区分は作る時点で検証する）
            var category = factory.create(UUID.randomUUID(), NAME);
            repository.add(category);
            var overlapping = spy(category);
            doReturn(new TaxCategory.Snapshot(category.id(), category.name(), List.of(
                    rate("10", null, APR_2028),
                    rate("8", APR_2027, null)), category.version())).when(overlapping).toSnapshot();

            // 操作と確認：業務のエラー TAX_RATE_PERIODS_OVERLAP で拒否される
            assertBusinessError(() -> repository.save(overlapping), TaxErrorCode.TAX_RATE_PERIODS_OVERLAP);
        }

        @Test
        @DisplayName("リポジトリが名前で指す排他制約が、マイグレーションに、検査を遅らせる設定で存在する")
        void periodNoOverlapConstraintExists() {
            // 操作：リポジトリが指す名前の制約を、カタログから読む
            // jOOQ は排他制約のコードを生成しないので、名前のずれをコンパイルで検出できない。ここで確認する
            var constraint = dsl.fetchOne("""
                            SELECT c.contype, c.condeferrable, c.condeferred
                            FROM pg_constraint c
                            JOIN pg_namespace n ON n.oid = c.connamespace
                            WHERE n.nspname = {0} AND c.conname = {1}""",
                    JooqTaxCategoryRepository.PERIOD_NO_OVERLAP.first(),
                    JooqTaxCategoryRepository.PERIOD_NO_OVERLAP.last());

            // 確認：遅延できる排他制約として存在する
            assertDeferredExclusionConstraint(constraint);
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 制約が、検査を遅らせる排他制約であることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>制約がある</li>
     *   <li>種類が排他制約（x）</li>
     *   <li>検査を遅らせられる</li>
     *   <li>既定で検査を遅らせる</li>
     * </ul>
     *
     * @param constraint {@code pg_constraint} から読んだ行。なければ null
     */
    private static void assertDeferredExclusionConstraint(Record constraint) {
        assertThat(constraint).as("制約 %s がない", JooqTaxCategoryRepository.PERIOD_NO_OVERLAP).isNotNull();
        assertThat(constraint.get("contype", String.class)).isEqualTo("x");
        assertThat(constraint.get("condeferrable", Boolean.class)).isTrue();
        assertThat(constraint.get("condeferred", Boolean.class)).isTrue();
    }

    /**
     * 再取得した税区分が、期待の税率を同じ順で持つことを確認する。
     *
     * <p>期待の率と期間は定数から作る。ID は保存のたびに振られるので、準備で読んだものを要素に入れて渡す。
     *
     * <p>確認すること：
     * <ul>
     *   <li>各税率の ID</li>
     *   <li>各税率の率</li>
     *   <li>各税率の適用期間</li>
     *   <li>一覧の順</li>
     * </ul>
     *
     * @param restored 再取得した税区分
     * @param expected 期待の税率（並びの順）
     */
    private static void assertRates(TaxCategory restored, TaxRateDraft... expected) {
        assertThat(restored.rates()).extracting(TaxRate::id, TaxRate::ratePercent, TaxRate::period)
                .containsExactly(Stream.of(expected).map(d -> tuple(d.id(), d.ratePercent(), d.period())).toArray(Tuple[]::new));
    }

    // ============================== 準備の補助 ==============================

    /**
     * 税率を持つ税区分を保存し、保存後の状態を返す。
     *
     * @param drafts 税率の一覧
     * @return 再取得した税区分
     */
    private TaxCategory saved(TaxRateDraft... drafts) {
        var category = factory.create(UUID.randomUUID(), NAME);
        repository.add(category);
        category.replaceRates(UUID::randomUUID, List.of(drafts), NOW);
        return saveAndReload(category);
    }

    /**
     * 10%（〜2027-04-01）と 8%（2027-04-01〜）を持つ税区分を保存し、保存後の状態を返す。
     *
     * @return 再取得した税区分（バージョンは 1）
     */
    private TaxCategory savedWithTwoRates() {
        return saved(
                newDraft("10", null, APR_2027),
                newDraft("8", APR_2027, null));
    }

    /**
     * 税区分を登録し、税率の一覧を連続する 3 件に置き換える（保存はしない）。
     *
     * <p>3 件は、10%（〜2027-04-01）、8%（2027-04-01〜2028-04-01）、10%（2028-04-01〜）。
     *
     * @return 置き換えた税区分
     */
    private TaxCategory addedWithContinuousRates() {
        var category = factory.create(UUID.randomUUID(), NAME);
        repository.add(category);
        category.replaceRates(UUID::randomUUID, List.of(
                newDraft("10", null, APR_2027),
                newDraft("8", APR_2027, APR_2028),
                newDraft("10", APR_2028, null)), NOW);
        return category;
    }

    /**
     * {@link #savedWithTwoRates()} の 1 件目の終了を 2028-04-01 に延ばし、2 件目を外して 12% を加える一覧を作る。
     *
     * @param firstId 1 件目の税率の ID（保存のときに振られるので、定数にできない）
     * @return 置き換えの一覧
     */
    private static List<TaxRateDraft> extendFirstAndReplaceSecond(UUID firstId) {
        return List.of(
                draft(firstId, "10", null, APR_2028),
                newDraft("12", APR_2028, null));
    }

    /**
     * 税区分を保存し、再取得する。
     *
     * @param category 保存する税区分
     * @return 再取得した税区分
     */
    private TaxCategory saveAndReload(TaxCategory category) {
        repository.save(category);
        return repository.findById(category.id()).orElseThrow();
    }

    /**
     * 税区分の税率の ID を、並びの順に返す。
     *
     * @param category 税区分
     * @return 税率の ID（保存や置き換えのときに振られるので、定数にできない）
     */
    private static List<UUID> idsOf(TaxCategory category) {
        return category.rates().stream().map(TaxRate::id).toList();
    }
}

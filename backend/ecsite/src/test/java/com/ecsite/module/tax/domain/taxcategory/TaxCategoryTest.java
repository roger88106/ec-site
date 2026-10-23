package com.ecsite.module.tax.domain.taxcategory;

import com.ecsite.module.tax.domain.TaxErrorCode;
import com.ecsite.module.tax.domain.taxcategory.vo.EffectivePeriod;
import com.ecsite.module.tax.domain.taxcategory.vo.RatePercent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 税区分の単体テスト（T104）。<br />
 * 現在時刻は引数で固定する。AC-4 の日時は日本時間なので、UTC に直して書く（2027-04-01 00:00 → 2027-03-31T15:00Z）。
 */
class TaxCategoryTest {

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

    /**
     * 過去の日時：2026-04-01 00:00（日本時間）
     */
    private static final Instant APR_2026 = Instant.parse("2026-03-31T15:00:00Z");

    /**
     * 1 分
     */
    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);

    /**
     * テストで使う ID
     */
    private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final TaxCategoryFactory factory = new TaxCategoryFactory();

    @Nested
    @DisplayName("登録")
    class Create {

        @Test
        @DisplayName("AC-1 名称で登録できる。税率はまだない")
        void createsWithName() {
            var category = factory.create("標準");

            assertThat(category.id()).isNotNull();
            assertThat(category.name()).isEqualTo("標準");
            assertThat(category.rates()).isEmpty();
            assertThat(category.version()).isZero();
        }

        @Test
        @DisplayName("名称は 50 文字まで")
        void acceptsNameUpTo50Characters() {
            assertThatCode(() -> factory.create("あ".repeat(50))).doesNotThrowAnyException();
            assertBusinessError(() -> factory.create("あ".repeat(51)), TaxErrorCode.INVALID_TAX_CATEGORY_NAME);
        }

        @Test
        @DisplayName("名称が空白なら拒否される")
        void rejectsBlankName() {
            assertBusinessError(() -> factory.create(" "), TaxErrorCode.INVALID_TAX_CATEGORY_NAME);
        }
    }

    @Nested
    @DisplayName("AC-3 一覧の形の検証")
    class ListShape {

        @Test
        @DisplayName("AC-3 期間が連続し、最後が無期限の一覧は受け付けられる。古い順に並ぶ")
        void acceptsContinuousList() {
            var category = factory.create("標準");

            // 並び順に関係なく検証する
            category.replaceRates(List.of(
                    newDraft("10", APR_2028, null),
                    newDraft("8", APR_2027, APR_2028),
                    newDraft("10", null, APR_2027)), NOW);

            assertThat(category.rates()).extracting(r -> r.period().effectiveFrom())
                    .containsExactly(null, APR_2027, APR_2028);
        }

        @Test
        @DisplayName("AC-3 期間が重複する一覧は拒否される")
        void rejectsOverlap() {
            var category = factory.create("標準");

            assertBusinessError(() -> category.replaceRates(List.of(
                    newDraft("10", null, APR_2028),
                    newDraft("8", APR_2027, null)), NOW), TaxErrorCode.TAX_RATE_PERIODS_OVERLAP);
        }

        @Test
        @DisplayName("AC-3 開始が空白の税率が 2 つある一覧は、重複として拒否される")
        void rejectsTwoUnboundedStarts() {
            var category = factory.create("標準");

            assertBusinessError(() -> category.replaceRates(List.of(
                    newDraft("10", null, APR_2027),
                    newDraft("8", null, null)), NOW), TaxErrorCode.TAX_RATE_PERIODS_OVERLAP);
        }

        @Test
        @DisplayName("AC-3 期間の間に途切れがある一覧は拒否される")
        void rejectsGap() {
            var category = factory.create("標準");

            assertBusinessError(() -> category.replaceRates(List.of(
                    newDraft("10", null, APR_2027),
                    newDraft("8", APR_2027.plus(ONE_MINUTE), null)), NOW), TaxErrorCode.TAX_RATE_PERIODS_GAP);
        }

        @Test
        @DisplayName("AC-3 最後の税率の終了が空白でない一覧は拒否される")
        void rejectsBoundedLast() {
            var category = factory.create("標準");

            assertBusinessError(() -> category.replaceRates(List.of(
                    newDraft("10", null, APR_2027),
                    newDraft("8", APR_2027, APR_2028)), NOW), TaxErrorCode.TAX_RATE_LAST_PERIOD_NOT_OPEN_ENDED);
        }

        @Test
        @DisplayName("AC-3 空の一覧は拒否される")
        void rejectsEmpty() {
            var category = factory.create("標準");

            assertBusinessError(() -> category.replaceRates(List.of(), NOW), TaxErrorCode.TAX_RATES_REQUIRED);
        }

        @Test
        @DisplayName("AC-3 拒否された一覧は、一部も反映されない")
        void rejectedListChangesNothing() {
            var existing = rate("10", null, null);
            var category = restore(existing);

            assertBusinessError(() -> category.replaceRates(List.of(
                    draft(existing.id(), "10", null, APR_2027),
                    newDraft("8", APR_2027, APR_2028)), NOW), TaxErrorCode.TAX_RATE_LAST_PERIOD_NOT_OPEN_ENDED);

            assertThat(category.rates()).containsExactly(existing);
        }
    }

    @Nested
    @DisplayName("AC-4 期間限定の税率")
    class LimitedTimeRate {

        @Test
        @DisplayName("AC-4 期間限定の税率に切り替え、元に戻せる。各時点の税率が正しい")
        void switchesAndRestores() {
            // Given：「標準」に 10%（開始・終了とも空白）がある。現在は 2026-10-08
            var base = rate("10", null, null);
            var category = restore(base);

            // When：基本の税率に終了を入れ、期間限定の税率と戻りの税率を追加する
            category.replaceRates(List.of(
                    draft(base.id(), "10", null, APR_2027),
                    newDraft("8", APR_2027, APR_2028),
                    newDraft("10", APR_2028, null)), NOW);

            // Then
            assertThat(rateAt(category, APR_2027.minus(ONE_MINUTE))).isEqualTo("10");
            assertThat(rateAt(category, APR_2027)).isEqualTo("8");
            assertThat(rateAt(category, APR_2028.minus(ONE_MINUTE))).isEqualTo("8");
            assertThat(rateAt(category, APR_2028)).isEqualTo("10");
            // 基本の税率は同じ税率として残る（ID が変わらない）
            assertThat(category.rates().getFirst().id()).isEqualTo(base.id());
        }

        @Test
        @DisplayName("AC-4 その時点を含む税率がなければ、税率はない")
        void noRateWhenNotCovered() {
            var category = factory.create("標準");
            category.replaceRates(List.of(newDraft("10", APR_2027, null)), NOW);

            assertThat(category.rateAt(NOW)).isEmpty();
            assertThat(category.rateAt(APR_2027)).isPresent();
        }

        @Test
        @DisplayName("AC-4 税率がない税区分は、どの時点でも税率がない")
        void noRateWithoutRates() {
            assertThat(factory.create("標準").rateAt(NOW)).isEmpty();
        }
    }

    @Nested
    @DisplayName("AC-5 過去の根拠を変えない")
    class PastUnchangeable {

        @Test
        @DisplayName("AC-5 現在より前の日時を、新しい税率の開始として設定できない")
        void rejectsPastStartForNewRate() {
            var category = factory.create("標準");

            assertBusinessError(() -> category.replaceRates(List.of(
                    newDraft("10", APR_2026, null)), NOW), TaxErrorCode.TAX_RATE_PAST_DATETIME);
        }

        @Test
        @DisplayName("AC-5 新しい税率の開始・終了は空白にできる")
        void acceptsBlankForNewRate() {
            var category = factory.create("標準");

            assertThatCode(() -> category.replaceRates(List.of(newDraft("10", null, null)), NOW))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("AC-5 開始済みの税率の率は変更できない（開始が空白のものを含む）")
        void rejectsChangingRateOfStarted() {
            var started = rate("10", null, null);
            var category = restore(started);

            assertBusinessError(() -> category.replaceRates(List.of(
                    draft(started.id(), "8", null, null)), NOW), TaxErrorCode.TAX_RATE_STARTED_UNCHANGEABLE);
        }

        @Test
        @DisplayName("AC-5 開始済みの税率の開始日時は変更できない")
        void rejectsChangingStartOfStarted() {
            var started = rate("10", APR_2026, null);
            var category = restore(started);

            assertBusinessError(() -> category.replaceRates(List.of(
                    draft(started.id(), "10", APR_2027, null)), NOW), TaxErrorCode.TAX_RATE_STARTED_UNCHANGEABLE);
        }

        @Test
        @DisplayName("AC-5 開始済みの税率は削除できない")
        void rejectsDeletingStarted() {
            var started = rate("10", null, null);
            var category = restore(started);

            assertBusinessError(() -> category.replaceRates(List.of(
                    newDraft("10", null, null)), NOW), TaxErrorCode.TAX_RATE_STARTED_UNCHANGEABLE);
        }

        @Test
        @DisplayName("AC-5 開始済みの税率の終了日時は、現在より後の日時に変更できる")
        void acceptsFutureEndForStarted() {
            var started = rate("10", null, APR_2028);
            var following = rate("8", APR_2028, null);
            var category = restore(started, following);

            category.replaceRates(List.of(
                    draft(started.id(), "10", null, APR_2027),
                    draft(following.id(), "8", APR_2027, null)), NOW);

            assertThat(rateAt(category, APR_2027)).isEqualTo("8");
        }

        @Test
        @DisplayName("AC-5 開始済みの税率の終了日時は、空白に変更できる")
        void acceptsBlankEndForStarted() {
            var started = rate("10", null, APR_2027);
            var following = rate("8", APR_2027, null);
            var category = restore(started, following);

            category.replaceRates(List.of(draft(started.id(), "10", null, null)), NOW);

            assertThat(rateAt(category, APR_2028)).isEqualTo("10");
        }

        @Test
        @DisplayName("AC-5 開始済みの税率の終了日時を、現在より前の日時に変更できない")
        void rejectsPastEndForStarted() {
            var started = rate("10", null, null);
            var category = restore(started);

            assertBusinessError(() -> category.replaceRates(List.of(
                    draft(started.id(), "10", null, APR_2026),
                    newDraft("8", APR_2026, null)), NOW), TaxErrorCode.TAX_RATE_PAST_DATETIME);
        }

        @Test
        @DisplayName("AC-5 終了済みの税率は変更できない")
        void rejectsChangingEnded() {
            var ended = rate("8", null, APR_2026);
            var current = rate("10", APR_2026, null);
            var category = restore(ended, current);

            assertBusinessError(() -> category.replaceRates(List.of(
                    draft(ended.id(), "5", null, APR_2026),
                    draft(current.id(), "10", APR_2026, null)), NOW), TaxErrorCode.TAX_RATE_ENDED_UNCHANGEABLE);
        }

        @Test
        @DisplayName("AC-5 終了済みの税率は削除できない")
        void rejectsDeletingEnded() {
            var ended = rate("8", null, APR_2026);
            var current = rate("10", APR_2026, null);
            var category = restore(ended, current);

            assertBusinessError(() -> category.replaceRates(List.of(
                    draft(current.id(), "10", APR_2026, null)), NOW), TaxErrorCode.TAX_RATE_ENDED_UNCHANGEABLE);
        }

        @Test
        @DisplayName("AC-5 終了済みの税率を変えずに残し、他を変更できる")
        void keepsEndedAsIs() {
            var ended = rate("8", null, APR_2026);
            var current = rate("10", APR_2026, null);
            var category = restore(ended, current);

            category.replaceRates(List.of(
                    draft(ended.id(), "8", null, APR_2026),
                    draft(current.id(), "10", APR_2026, APR_2027),
                    newDraft("12", APR_2027, null)), NOW);

            assertThat(category.rates()).hasSize(3).first().isEqualTo(ended);
        }

        @Test
        @DisplayName("AC-5 未開始の税率は変更できる")
        void acceptsChangingNotStarted() {
            var started = rate("10", null, APR_2027);
            var future = rate("8", APR_2027, null);
            var category = restore(started, future);

            category.replaceRates(List.of(
                    draft(started.id(), "10", null, APR_2028),
                    draft(future.id(), "12", APR_2028, null)), NOW);

            assertThat(rateAt(category, APR_2027)).isEqualTo("10");
            assertThat(rateAt(category, APR_2028)).isEqualTo("12");
        }

        @Test
        @DisplayName("AC-5 未開始の税率は削除できる")
        void acceptsDeletingNotStarted() {
            var started = rate("10", null, APR_2027);
            var future = rate("8", APR_2027, null);
            var category = restore(started, future);

            category.replaceRates(List.of(draft(started.id(), "10", null, null)), NOW);

            assertThat(category.rates()).extracting(TaxRate::id).containsExactly(started.id());
        }
    }

    @Nested
    @DisplayName("既存の税率の指定")
    class ExistingRateReference {

        @Test
        @DisplayName("税区分にない税率の ID は拒否される")
        void rejectsUnknownId() {
            var category = restore(rate("10", null, null));

            assertBusinessError(() -> category.replaceRates(List.of(
                    draft(UUID.randomUUID(), "10", null, null)), NOW), TaxErrorCode.INVALID_TAX_RATE_ID);
        }

        @Test
        @DisplayName("同じ税率の ID を 2 回指定すると拒否される")
        void rejectsDuplicatedId() {
            var started = rate("10", null, APR_2027);
            var category = restore(started);

            assertBusinessError(() -> category.replaceRates(List.of(
                    draft(started.id(), "10", null, APR_2027),
                    draft(started.id(), "10", APR_2027, null)), NOW), TaxErrorCode.INVALID_TAX_RATE_ID);
        }
    }

    // ============================== 補助 ==============================

    /**
     * 税率の一覧を持つ税区分を、保存済みのものとして復元する
     */
    private TaxCategory restore(TaxRate... rates) {
        return factory.restore(new TaxCategory.Snapshot(CATEGORY_ID, "標準", List.of(rates), 3));
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

    /**
     * 指定した時点の率を、比べやすい文字列（例：10）で返す
     */
    private static String rateAt(TaxCategory category, Instant at) {
        return category.rateAt(at).orElseThrow().ratePercent().value().toPlainString();
    }
}

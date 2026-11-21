package com.ecsite.module.tax.domain.taxcategory;

import com.ecsite.module.tax.domain.TaxErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.ecsite.module.tax.domain.taxcategory.TestTaxRates.draft;
import static com.ecsite.module.tax.domain.taxcategory.TestTaxRates.newDraft;
import static com.ecsite.module.tax.domain.taxcategory.TestTaxRates.rate;
import static com.ecsite.support.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 税区分の単体テスト
 *
 * <p>現在時刻は引数で固定する。適用開始の日時は日本時間なので、UTC に直して書く（2027-04-01 00:00 → 2027-03-31T15:00Z）。
 */
class TaxCategoryTest {

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
     * 過去の日時
     *
     * <p>日本時間の 2026-04-01 00:00。
     */
    private static final Instant APR_2026 = Instant.parse("2026-03-31T15:00:00Z");

    /**
     * 1 分
     */
    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);

    /**
     * 復元する税区分の ID
     */
    private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /**
     * 登録する税区分の ID
     */
    private static final UUID NEW_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");

    /**
     * 新しい税率に使う ID
     */
    private static final UUID NEW_RATE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d2");

    /**
     * 税区分の名称
     */
    private static final String NAME = "標準";

    /**
     * 名称の最大の文字数
     */
    private static final int NAME_MAX_LENGTH = 50;

    /**
     * 基本の税率（%）
     */
    private static final String BASE_RATE = "10";

    /**
     * 期間限定の税率（%）
     */
    private static final String LIMITED_RATE = "8";

    /**
     * 税区分の生成
     */
    private final TaxCategoryFactory factory = new TaxCategoryFactory();

    // ============================== テスト ==============================

    @Nested
    @DisplayName("登録")
    class Create {

        @Test
        @DisplayName("AC-1 名称で登録できる")
        void createsWithName() {
            // 操作：名称で登録する
            var category = factory.create(NEW_ID, NAME);

            // 確認：渡した ID と名称を持つ
            assertThat(category.id()).isEqualTo(NEW_ID);
            assertThat(category.name()).isEqualTo(NAME);
        }

        @Test
        @DisplayName("AC-1 登録したばかりの税区分は、税率を持たない")
        void hasNoRatesWhenCreated() {
            // 操作：名称で登録する
            var category = factory.create(UUID.randomUUID(), NAME);

            // 確認：税率がない
            assertThat(category.rates()).isEmpty();
        }

        @Test
        @DisplayName("登録したばかりの税区分のバージョンは 0")
        void startsAtVersionZero() {
            // 操作：名称で登録する
            var category = factory.create(UUID.randomUUID(), NAME);

            // 確認：バージョンが 0
            assertThat(category.version()).isZero();
        }

        @Test
        @DisplayName("名称は 1 文字でも登録できる")
        void acceptsOneCharacterName() {
            // 操作と確認：1 文字の名称は拒否されない
            assertThatCode(() -> factory.create(UUID.randomUUID(), "あ")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("名称は 50 文字まで登録できる")
        void acceptsNameUpToMaxLength() {
            // 操作と確認：50 文字の名称は拒否されない
            assertThatCode(() -> factory.create(UUID.randomUUID(), "あ".repeat(NAME_MAX_LENGTH))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("51 文字以上の名称は拒否される")
        void rejectsNameOverMaxLength() {
            // 操作と確認：業務のエラー INVALID_TAX_CATEGORY_NAME で拒否される
            assertBusinessError(() -> factory.create(UUID.randomUUID(), "あ".repeat(NAME_MAX_LENGTH + 1)), TaxErrorCode.INVALID_TAX_CATEGORY_NAME);
        }

        @Test
        @DisplayName("名称が空白なら拒否される")
        void rejectsBlankName() {
            // 操作と確認：業務のエラー INVALID_TAX_CATEGORY_NAME で拒否される
            assertBusinessError(() -> factory.create(UUID.randomUUID(), " "), TaxErrorCode.INVALID_TAX_CATEGORY_NAME);
        }
    }

    @Nested
    @DisplayName("一覧の形の検証")
    class ListShape {

        @Test
        @DisplayName("AC-3 期間が連続し、最後が無期限の一覧は受け付けられる")
        void acceptsContinuousList() {
            // 準備：税率のない税区分
            var category = factory.create(UUID.randomUUID(), NAME);

            // 操作と確認：期間が連続し、最後が無期限の一覧は拒否されない
            assertThatCode(() -> category.replaceRates(List.of(
                    newDraft("10", null, APR_2027),
                    newDraft("8", APR_2027, APR_2028),
                    newDraft("10", APR_2028, null)), NOW)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("AC-3 ID のない税率は、案が持つ新しい ID で加わる")
        void usesNewIdOfDraft() {
            // 準備：税率のない税区分
            var category = factory.create(UUID.randomUUID(), NAME);

            // 操作：新しい ID を決めた、ID のない税率で置き換える
            category.replaceRates(List.of(newDraft(NEW_RATE_ID, "10", null, null)), NOW);

            // 確認：税率の ID が案の新しい ID
            assertThat(category.rates()).extracting(TaxRate::id).containsExactly(NEW_RATE_ID);
        }

        @Test
        @DisplayName("AC-3 受け付けた一覧は、開始の古い順に並ぶ")
        void sortsByStart() {
            // 準備：税率のない税区分
            var category = factory.create(UUID.randomUUID(), NAME);

            // 操作：開始の新しい順に並べた一覧で置き換える
            category.replaceRates(List.of(
                    newDraft("10", APR_2028, null),
                    newDraft("8", APR_2027, APR_2028),
                    newDraft("10", null, APR_2027)), NOW);

            // 確認：開始の古い順（空白が先頭）
            assertThat(category.rates()).extracting(r -> r.period().effectiveFrom())
                    .containsExactly(null, APR_2027, APR_2028);
        }

        @Test
        @DisplayName("AC-3 期間が重複する一覧は拒否される")
        void rejectsOverlap() {
            // 準備：税率のない税区分
            var category = factory.create(UUID.randomUUID(), NAME);

            // 操作と確認：業務のエラー TAX_RATE_PERIODS_OVERLAP で拒否される
            assertBusinessError(() -> category.replaceRates(List.of(
                    newDraft("10", null, APR_2028),
                    newDraft("8", APR_2027, null)), NOW), TaxErrorCode.TAX_RATE_PERIODS_OVERLAP);
        }

        @Test
        @DisplayName("AC-3 開始が空白の税率が 2 つある一覧は、重複として拒否される")
        void rejectsTwoUnboundedStarts() {
            // 準備：税率のない税区分
            var category = factory.create(UUID.randomUUID(), NAME);

            // 操作と確認：業務のエラー TAX_RATE_PERIODS_OVERLAP で拒否される
            assertBusinessError(() -> category.replaceRates(List.of(
                    newDraft("10", null, APR_2027),
                    newDraft("8", null, null)), NOW), TaxErrorCode.TAX_RATE_PERIODS_OVERLAP);
        }

        @Test
        @DisplayName("AC-3 終了が空白の税率の後に税率がある一覧は、重複として拒否される")
        void rejectsRateAfterUnboundedEnd() {
            // 準備：税率のない税区分
            var category = factory.create(UUID.randomUUID(), NAME);

            // 操作と確認：無期限の税率と、2027-04-01 からの税率は、業務のエラー TAX_RATE_PERIODS_OVERLAP で拒否される
            assertBusinessError(() -> category.replaceRates(List.of(
                    newDraft("10", null, null),
                    newDraft("8", APR_2027, null)), NOW), TaxErrorCode.TAX_RATE_PERIODS_OVERLAP);
        }

        @Test
        @DisplayName("AC-3 期間の間に途切れがある一覧は拒否される")
        void rejectsGap() {
            // 準備：税率のない税区分
            var category = factory.create(UUID.randomUUID(), NAME);

            // 操作と確認：1 分の途切れがあれば、業務のエラー TAX_RATE_PERIODS_GAP で拒否される
            assertBusinessError(() -> category.replaceRates(List.of(
                    newDraft("10", null, APR_2027),
                    newDraft("8", APR_2027.plus(ONE_MINUTE), null)), NOW), TaxErrorCode.TAX_RATE_PERIODS_GAP);
        }

        @Test
        @DisplayName("AC-3 最後の税率の終了が空白でない一覧は拒否される")
        void rejectsBoundedLast() {
            // 準備：税率のない税区分
            var category = factory.create(UUID.randomUUID(), NAME);

            // 操作と確認：業務のエラー TAX_RATE_LAST_PERIOD_NOT_OPEN_ENDED で拒否される
            assertBusinessError(() -> category.replaceRates(List.of(
                    newDraft("10", null, APR_2027),
                    newDraft("8", APR_2027, APR_2028)), NOW), TaxErrorCode.TAX_RATE_LAST_PERIOD_NOT_OPEN_ENDED);
        }

        @Test
        @DisplayName("AC-3 空の一覧は拒否される")
        void rejectsEmpty() {
            // 準備：税率のない税区分
            var category = factory.create(UUID.randomUUID(), NAME);

            // 操作と確認：業務のエラー TAX_RATES_REQUIRED で拒否される
            assertBusinessError(() -> category.replaceRates(List.of(), NOW), TaxErrorCode.TAX_RATES_REQUIRED);
        }

        @Test
        @DisplayName("AC-3 拒否された一覧は、一部も反映されない")
        void rejectedListChangesNothing() {
            // 準備：10%（開始・終了とも空白）を持つ税区分
            var existing = rate("10", null, null);
            var category = restore(existing);

            // 操作：既存の税率の終了の変更を含み、最後が無期限でない一覧で置き換え、拒否される
            assertBusinessError(() -> category.replaceRates(List.of(
                    draft(existing.id(), "10", null, APR_2027),
                    newDraft("8", APR_2027, APR_2028)), NOW), TaxErrorCode.TAX_RATE_LAST_PERIOD_NOT_OPEN_ENDED);

            // 確認：税率の一覧は元のまま
            assertThat(category.rates()).usingRecursiveFieldByFieldElementComparator().containsExactly(existing);
        }
    }

    @Nested
    @DisplayName("期間限定の税率")
    class LimitedTimeRate {

        @Test
        @DisplayName("AC-4 期間限定の税率に切り替え、元に戻せる")
        void switchesAndRestores() {
            // 準備：基本の税率（開始・終了とも空白）を持つ税区分
            var base = rate(BASE_RATE, null, null);
            var category = restore(base);

            // 操作：基本の税率に終了を入れ、期間限定の税率と戻りの税率を追加する
            switchToLimitedTimeRate(category, base.id());

            // 確認：切り替えの前後の各時点の税率
            assertRatesAroundLimitedTime(category);
        }

        @Test
        @DisplayName("AC-4 期間限定の税率に切り替えても、基本の税率は同じ税率として残る")
        void keepsBaseRateIdentity() {
            // 準備：基本の税率（開始・終了とも空白）を持つ税区分
            var base = rate(BASE_RATE, null, null);
            var category = restore(base);

            // 操作：基本の税率に終了を入れ、期間限定の税率と戻りの税率を追加する
            switchToLimitedTimeRate(category, base.id());

            // 確認：先頭の税率の ID が、基本の税率のまま
            assertThat(category.rates().getFirst().id()).isEqualTo(base.id());
        }

        @Test
        @DisplayName("AC-4 その時点を含む税率がなければ、税率はない")
        void noRateWhenNotCovered() {
            // 準備：2027-04-01 から始まる税率だけを持つ税区分
            var category = factory.create(UUID.randomUUID(), NAME);
            category.replaceRates(List.of(newDraft("10", APR_2027, null)), NOW);

            // 確認（前提）：開始の時点には税率がある
            assertThat(category.rateAt(APR_2027)).isPresent();

            // 操作と確認：開始より前の現在には税率がない
            assertThat(category.rateAt(NOW)).isEmpty();
        }

        @Test
        @DisplayName("AC-4 税率がない税区分は、どの時点でも税率がない")
        void noRateWithoutRates() {
            // 操作と確認：登録したばかりの税区分には、現在の税率がない
            assertThat(factory.create(UUID.randomUUID(), NAME).rateAt(NOW)).isEmpty();
        }
    }

    @Nested
    @DisplayName("新たに設定する日時")
    class NewDateTime {

        @Test
        @DisplayName("AC-5 現在より前の日時を、新しい税率の開始として設定できない")
        void rejectsPastStartForNewRate() {
            // 準備：税率のない税区分
            var category = factory.create(UUID.randomUUID(), NAME);

            // 操作と確認：業務のエラー TAX_RATE_PAST_DATETIME で拒否される
            assertBusinessError(() -> category.replaceRates(List.of(
                    newDraft("10", APR_2026, null)), NOW), TaxErrorCode.TAX_RATE_PAST_DATETIME);
        }

        @Test
        @DisplayName("AC-5 現在より前の日時を、新しい税率の終了として設定できない")
        void rejectsPastEndForNewRate() {
            // 準備：税率のない税区分
            var category = factory.create(UUID.randomUUID(), NAME);

            // 操作と確認：一覧の形の誤り（最後が無期限でない）より先に、業務のエラー TAX_RATE_PAST_DATETIME で拒否される
            assertBusinessError(() -> category.replaceRates(List.of(
                    newDraft("10", null, APR_2026)), NOW), TaxErrorCode.TAX_RATE_PAST_DATETIME);
        }

        @Test
        @DisplayName("AC-5 現在ちょうどの日時を、新しい税率の開始として設定できない")
        void rejectsNowStartForNewRate() {
            // 準備：税率のない税区分
            var category = factory.create(UUID.randomUUID(), NAME);

            // 操作と確認：業務のエラー TAX_RATE_PAST_DATETIME で拒否される
            assertBusinessError(() -> category.replaceRates(List.of(
                    newDraft("10", NOW, null)), NOW), TaxErrorCode.TAX_RATE_PAST_DATETIME);
        }

        @Test
        @DisplayName("AC-5 現在ちょうどの日時を、新しい税率の終了として設定できない")
        void rejectsNowEndForNewRate() {
            // 準備：税率のない税区分
            var category = factory.create(UUID.randomUUID(), NAME);

            // 操作と確認：一覧の形の誤り（最後が無期限でない）より先に、業務のエラー TAX_RATE_PAST_DATETIME で拒否される
            assertBusinessError(() -> category.replaceRates(List.of(
                    newDraft("10", null, NOW)), NOW), TaxErrorCode.TAX_RATE_PAST_DATETIME);
        }

        @Test
        @DisplayName("AC-5 新しい税率の開始・終了は空白にできる")
        void acceptsBlankForNewRate() {
            // 準備：税率のない税区分
            var category = factory.create(UUID.randomUUID(), NAME);

            // 操作と確認：開始・終了とも空白の税率は拒否されない
            assertThatCode(() -> category.replaceRates(List.of(newDraft("10", null, null)), NOW))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("開始済みの税率")
    class StartedRate {

        @Test
        @DisplayName("AC-5 開始済みの税率の率は変更できない（開始が空白のものを含む）")
        void rejectsChangingRateOfStarted() {
            // 準備：開始が空白の、開始済みの税率を持つ税区分
            var started = rate("10", null, null);
            var category = restore(started);

            // 操作と確認：率を 8% に変えると、業務のエラー TAX_RATE_STARTED_UNCHANGEABLE で拒否される
            assertBusinessError(() -> category.replaceRates(List.of(
                    draft(started.id(), "8", null, null)), NOW), TaxErrorCode.TAX_RATE_STARTED_UNCHANGEABLE);
        }

        @Test
        @DisplayName("AC-5 開始済みの税率の開始日時は変更できない")
        void rejectsChangingStartOfStarted() {
            // 準備：2026-04-01 に開始済みの税率を持つ税区分
            var started = rate("10", APR_2026, null);
            var category = restore(started);

            // 操作と確認：開始を 2027-04-01 に変えると、業務のエラー TAX_RATE_STARTED_UNCHANGEABLE で拒否される
            assertBusinessError(() -> category.replaceRates(List.of(
                    draft(started.id(), "10", APR_2027, null)), NOW), TaxErrorCode.TAX_RATE_STARTED_UNCHANGEABLE);
        }

        @Test
        @DisplayName("AC-5 開始済みの税率は削除できない")
        void rejectsDeletingStarted() {
            // 準備：開始済みの税率を持つ税区分
            var started = rate("10", null, null);
            var category = restore(started);

            // 操作と確認：既存の税率を外した一覧は、業務のエラー TAX_RATE_STARTED_UNCHANGEABLE で拒否される
            assertBusinessError(() -> category.replaceRates(List.of(
                    newDraft("10", null, null)), NOW), TaxErrorCode.TAX_RATE_STARTED_UNCHANGEABLE);
        }

        @Test
        @DisplayName("AC-5 開始済みの税率の終了日時は、現在より後の日時に変更できる")
        void acceptsFutureEndForStarted() {
            // 準備：2028-04-01 に終わる開始済みの税率と、その後の税率を持つ税区分
            var started = rate("10", null, APR_2028);
            var following = rate("8", APR_2028, null);
            var category = restore(started, following);

            // 操作：開始済みの税率の終了を 2027-04-01 に早める
            category.replaceRates(List.of(
                    draft(started.id(), "10", null, APR_2027),
                    draft(following.id(), "8", APR_2027, null)), NOW);

            // 確認：2027-04-01 から後の税率になる
            assertThat(rateAt(category, APR_2027)).isEqualTo("8");
        }

        @Test
        @DisplayName("AC-5 開始済みの税率の終了日時は、空白に変更できる")
        void acceptsBlankEndForStarted() {
            // 準備：2027-04-01 に終わる開始済みの税率と、その後の税率を持つ税区分
            var started = rate("10", null, APR_2027);
            var following = rate("8", APR_2027, null);
            var category = restore(started, following);

            // 操作：開始済みの税率の終了を空白にし、後の税率を削除する
            category.replaceRates(List.of(draft(started.id(), "10", null, null)), NOW);

            // 確認：2028-04-01 も開始済みの税率のまま
            assertThat(rateAt(category, APR_2028)).isEqualTo("10");
        }

        @Test
        @DisplayName("AC-5 開始済みの税率の終了日時を、現在より前の日時に変更できない")
        void rejectsPastEndForStarted() {
            // 準備：開始済みの税率を持つ税区分
            var started = rate("10", null, null);
            var category = restore(started);

            // 操作と確認：終了を 2026-04-01 にすると、業務のエラー TAX_RATE_PAST_DATETIME で拒否される
            assertBusinessError(() -> category.replaceRates(List.of(
                    draft(started.id(), "10", null, APR_2026),
                    newDraft("8", APR_2026, null)), NOW), TaxErrorCode.TAX_RATE_PAST_DATETIME);
        }
    }

    @Nested
    @DisplayName("終了済みの税率")
    class EndedRate {

        @Test
        @DisplayName("AC-5 終了済みの税率は変更できない")
        void rejectsChangingEnded() {
            // 準備：2026-04-01 に終了済みの税率と、今の税率を持つ税区分
            var ended = rate("8", null, APR_2026);
            var current = rate("10", APR_2026, null);
            var category = restore(ended, current);

            // 操作と確認：終了済みの率を 5% に変えると、業務のエラー TAX_RATE_ENDED_UNCHANGEABLE で拒否される
            assertBusinessError(() -> category.replaceRates(List.of(
                    draft(ended.id(), "5", null, APR_2026),
                    draft(current.id(), "10", APR_2026, null)), NOW), TaxErrorCode.TAX_RATE_ENDED_UNCHANGEABLE);
        }

        @Test
        @DisplayName("AC-5 終了済みの税率は、率を変えなくても、終了を延ばせない")
        void rejectsExtendingEnded() {
            // 準備：2026-04-01 に終了済みの税率と、今の税率を持つ税区分
            var ended = rate("8", null, APR_2026);
            var current = rate("10", APR_2026, null);
            var category = restore(ended, current);

            // 操作と確認：率は 8% のまま終了を 2027-04-01 に延ばすと、業務のエラー TAX_RATE_ENDED_UNCHANGEABLE で拒否される
            assertBusinessError(() -> category.replaceRates(List.of(
                    draft(ended.id(), "8", null, APR_2027),
                    draft(current.id(), "10", APR_2026, null)), NOW), TaxErrorCode.TAX_RATE_ENDED_UNCHANGEABLE);
        }

        @Test
        @DisplayName("AC-5 終了済みの税率は削除できない")
        void rejectsDeletingEnded() {
            // 準備：2026-04-01 に終了済みの税率と、今の税率を持つ税区分
            var ended = rate("8", null, APR_2026);
            var current = rate("10", APR_2026, null);
            var category = restore(ended, current);

            // 操作と確認：終了済みの税率を外した一覧は、業務のエラー TAX_RATE_ENDED_UNCHANGEABLE で拒否される
            assertBusinessError(() -> category.replaceRates(List.of(
                    draft(current.id(), "10", APR_2026, null)), NOW), TaxErrorCode.TAX_RATE_ENDED_UNCHANGEABLE);
        }

        @Test
        @DisplayName("AC-5 終了済みの税率を変えずに残し、他を変更できる")
        void keepsEndedAsIs() {
            // 準備：2026-04-01 に終了済みの税率と、今の税率を持つ税区分
            var ended = rate("8", null, APR_2026);
            var current = rate("10", APR_2026, null);
            var category = restore(ended, current);

            // 操作：終了済みの税率はそのまま、今の税率に終了を入れ、新しい税率を追加する
            category.replaceRates(List.of(
                    draft(ended.id(), "8", null, APR_2026),
                    draft(current.id(), "10", APR_2026, APR_2027),
                    newDraft("12", APR_2027, null)), NOW);

            // 確認：3 件になり、先頭は終了済みの税率のまま
            assertThat(category.rates()).hasSize(3).first().usingRecursiveComparison().isEqualTo(ended);
        }
    }

    @Nested
    @DisplayName("未開始の税率")
    class NotStartedRate {

        @Test
        @DisplayName("AC-5 未開始の税率は変更できる")
        void acceptsChangingNotStarted() {
            // 準備：2027-04-01 に終わる開始済みの税率と、2027-04-01 から始まる未開始の税率を持つ税区分
            var started = rate("10", null, APR_2027);
            var future = rate("8", APR_2027, null);
            var category = restore(started, future);

            // 操作：未開始の税率の開始を 2028-04-01、率を 12% に変える
            category.replaceRates(List.of(
                    draft(started.id(), "10", null, APR_2028),
                    draft(future.id(), "12", APR_2028, null)), NOW);

            // 確認：2028-04-01 の前は開始済みの税率、2028-04-01 から変えた率
            assertThat(rateAt(category, APR_2027)).isEqualTo("10");
            assertThat(rateAt(category, APR_2028)).isEqualTo("12");
        }

        @Test
        @DisplayName("AC-5 未開始の税率は削除できる")
        void acceptsDeletingNotStarted() {
            // 準備：2027-04-01 に終わる開始済みの税率と、2027-04-01 から始まる未開始の税率を持つ税区分
            var started = rate("10", null, APR_2027);
            var future = rate("8", APR_2027, null);
            var category = restore(started, future);

            // 操作：未開始の税率を外し、開始済みの税率の終了を空白にする
            category.replaceRates(List.of(draft(started.id(), "10", null, null)), NOW);

            // 確認：開始済みの税率だけが残る
            assertThat(category.rates()).extracting(TaxRate::id).containsExactly(started.id());
        }

        @Test
        @DisplayName("AC-5 未開始の税率の開始を、現在より前の日時に変更できない")
        void rejectsMovingNotStartedToPast() {
            // 準備：2027-04-01 に終わる開始済みの税率と、2027-04-01 から始まる未開始の税率を持つ税区分
            var started = rate("10", null, APR_2027);
            var future = rate("8", APR_2027, null);
            var category = restore(started, future);

            // 操作と確認：未開始の税率の開始を 2026-04-01 に変えると、業務のエラー TAX_RATE_PAST_DATETIME で拒否される
            assertBusinessError(() -> category.replaceRates(List.of(
                    draft(started.id(), "10", null, APR_2027),
                    draft(future.id(), "8", APR_2026, null)), NOW), TaxErrorCode.TAX_RATE_PAST_DATETIME);
        }
    }

    @Nested
    @DisplayName("既存の税率の指定")
    class ExistingRateReference {

        @Test
        @DisplayName("税区分にない税率の ID は拒否される")
        void rejectsUnknownId() {
            // 準備：税率を 1 つ持つ税区分
            var category = restore(rate("10", null, null));

            // 操作と確認：業務のエラー INVALID_TAX_RATE_ID で拒否される
            assertBusinessError(() -> category.replaceRates(List.of(
                    draft(UUID.randomUUID(), "10", null, null)), NOW), TaxErrorCode.INVALID_TAX_RATE_ID);
        }

        @Test
        @DisplayName("同じ税率の ID を 2 回指定すると拒否される")
        void rejectsDuplicatedId() {
            // 準備：税率を 1 つ持つ税区分
            var started = rate("10", null, null);
            var category = restore(started);

            // 操作と確認：業務のエラー INVALID_TAX_RATE_ID で拒否される
            assertBusinessError(() -> category.replaceRates(List.of(
                    draft(started.id(), "10", null, APR_2027),
                    draft(started.id(), "10", APR_2027, null)), NOW), TaxErrorCode.INVALID_TAX_RATE_ID);
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 期間限定の税率に切り替えた税区分の、各時点の税率を確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>2027-03-31 23:59 は基本の税率</li>
     *   <li>2027-04-01 00:00 は期間限定の税率</li>
     *   <li>2028-03-31 23:59 は期間限定の税率</li>
     *   <li>2028-04-01 00:00 は基本の税率</li>
     * </ul>
     *
     * @param category 確認する税区分
     */
    private static void assertRatesAroundLimitedTime(TaxCategory category) {
        assertThat(rateAt(category, APR_2027.minus(ONE_MINUTE))).isEqualTo(BASE_RATE);
        assertThat(rateAt(category, APR_2027)).isEqualTo(LIMITED_RATE);
        assertThat(rateAt(category, APR_2028.minus(ONE_MINUTE))).isEqualTo(LIMITED_RATE);
        assertThat(rateAt(category, APR_2028)).isEqualTo(BASE_RATE);
    }

    /**
     * 指定した時点の率を、比べやすい文字列（例：10）で返す。
     *
     * @param category 税区分
     * @param at       時点
     * @return 率（%）
     */
    private static String rateAt(TaxCategory category, Instant at) {
        return category.rateAt(at).orElseThrow().ratePercent().value().toPlainString();
    }

    // ============================== 準備の補助 ==============================

    /**
     * 税率の一覧を持つ税区分を、保存済みのものとして復元する。
     *
     * @param rates 税率の一覧
     * @return 税区分
     */
    private TaxCategory restore(TaxRate... rates) {
        return factory.restore(new TaxCategory.Snapshot(CATEGORY_ID, NAME, List.of(rates), 3));
    }

    /**
     * 基本の税率に終了を入れ、期間限定の税率と戻りの税率を追加する。
     *
     * <p>置き換え後の一覧は、基本の税率（〜2027-04-01）、期間限定の税率（2027-04-01〜2028-04-01）、基本の税率（2028-04-01〜）。
     *
     * @param category 基本の税率だけを持つ税区分
     * @param baseId   基本の税率の ID
     */
    private static void switchToLimitedTimeRate(TaxCategory category, UUID baseId) {
        category.replaceRates(List.of(
                draft(baseId, BASE_RATE, null, APR_2027),
                newDraft(LIMITED_RATE, APR_2027, APR_2028),
                newDraft(BASE_RATE, APR_2028, null)), NOW);
    }
}

package com.ecsite.module.tax.domain.taxcategory;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.module.tax.domain.TaxErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.ecsite.module.tax.domain.taxcategory.TestTaxRates.rate;
import static com.ecsite.support.BusinessErrorAssertions.assertBusinessError;
import static com.ecsite.support.BusinessErrorAssertions.assertSystemError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 税率の一覧の単体テスト
 *
 * <p>期間の連続の検査を、コンストラクターと、税区分の作成・復元・置き換えの経路で確認する。
 */
class TaxRatesTest {

    // ============================== 定数 ==============================

    /**
     * 期間の境目の 1 つ目
     */
    private static final Instant T1 = Instant.parse("2027-03-31T15:00:00Z");

    /**
     * 期間の境目の 2 つ目
     */
    private static final Instant T2 = Instant.parse("2028-03-31T15:00:00Z");

    /**
     * 税区分の生成
     */
    private final TaxCategoryFactory factory = new TaxCategoryFactory();

    // ============================== テスト ==============================

    @Test
    @DisplayName("空の一覧は作れる")
    void acceptsEmpty() {
        // 操作と確認：税率のない一覧が作れる
        assertThat(new TaxRates(List.of()).list()).isEmpty();
    }

    @Test
    @DisplayName("途切れなくつながり、最後の終了が空白の一覧は、並びが逆でも開始の古い順で作れる")
    void acceptsContinuousAndSorts() {
        // 準備：開始の新しい順に渡す
        var later = rate("10", T1, null);
        var earlier = rate("8", null, T1);

        // 操作：一覧を作る
        var rates = new TaxRates(List.of(later, earlier));

        // 確認：開始の古い順
        assertThat(rates.list()).extracting(TaxRate::id).containsExactly(earlier.id(), later.id());
    }

    @Test
    @DisplayName("期間が重なると、業務のエラー TAX_RATE_PERIODS_OVERLAP で拒否される")
    void rejectsOverlap() {
        // 操作と確認：2 件目の開始が 1 件目の終了より前
        assertBusinessError(() -> new TaxRates(List.of(rate("8", null, T2), rate("10", T1, null))),
                TaxErrorCode.TAX_RATE_PERIODS_OVERLAP);
    }

    @Test
    @DisplayName("期間が途切れると、業務のエラー TAX_RATE_PERIODS_GAP で拒否される")
    void rejectsGap() {
        // 操作と確認：1 件目の終了と 2 件目の開始の間が空く
        assertBusinessError(() -> new TaxRates(List.of(rate("8", null, T1), rate("10", T2, null))),
                TaxErrorCode.TAX_RATE_PERIODS_GAP);
    }

    @Test
    @DisplayName("前の終了と次の開始がちょうど同じなら、つながるので作れる")
    void acceptsExactlyAdjacent() {
        // 操作と確認：境目が同じ時刻なら、例外にならない
        assertThatCode(() -> new TaxRates(List.of(rate("8", null, T1), rate("10", T1, null)))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("次の開始が前の終了より最小の単位だけ前だと、業務のエラー TAX_RATE_PERIODS_OVERLAP で拒否される")
    void rejectsOverlapByOneUnit() {
        // 操作と確認：境目の 1 つ内側で重なる
        assertBusinessError(() -> new TaxRates(List.of(rate("8", null, T1), rate("10", T1.minusNanos(1), null))),
                TaxErrorCode.TAX_RATE_PERIODS_OVERLAP);
    }

    @Test
    @DisplayName("次の開始が前の終了より最小の単位だけ後だと、業務のエラー TAX_RATE_PERIODS_GAP で拒否される")
    void rejectsGapByOneUnit() {
        // 操作と確認：境目の 1 つ外で途切れる
        assertBusinessError(() -> new TaxRates(List.of(rate("8", null, T1), rate("10", T1.plusNanos(1), null))),
                TaxErrorCode.TAX_RATE_PERIODS_GAP);
    }

    @Test
    @DisplayName("最後の税率の終了が空白でないと、業務のエラー TAX_RATE_LAST_PERIOD_NOT_OPEN_ENDED で拒否される")
    void rejectsClosedLastPeriod() {
        // 操作と確認：1 件だけで終了がある
        assertBusinessError(() -> new TaxRates(List.of(rate("8", null, T1))), TaxErrorCode.TAX_RATE_LAST_PERIOD_NOT_OPEN_ENDED);
    }

    @Test
    @DisplayName("税区分の復元で期間が途切れていると、復元のエラーになる")
    void restoreRejectsGap() {
        // 準備：期間が途切れる税率の一覧のスナップショット
        var snapshot = new TaxCategory.Snapshot(UUID.randomUUID(), "標準", List.of(rate("8", null, T1), rate("10", T2, null)), 0);

        // 操作と確認：復元のエラーで拒否される
        assertSystemError(() -> factory.restore(snapshot), CommonErrorCode.RESTORE_ERROR);
    }

    @Test
    @DisplayName("税区分の作成・復元・置き換えのどれでも、正しい税率の一覧が通る")
    void validRatesPassEveryPath() {
        // 準備：途切れず、最後の終了が空白の税率
        var created = factory.create(UUID.randomUUID(), "標準");
        var rates = List.of(rate("8", null, T1), rate("10", T1, null));

        // 操作：復元と、置き換え
        var restored = factory.restore(new TaxCategory.Snapshot(created.id(), "標準", rates, 0));
        created.replaceRates(UUID::randomUUID, List.of(TestTaxRates.newDraft("10", T1, null)), Instant.parse("2026-10-07T15:00:00Z"));

        // 確認：どちらも一覧を持つ
        assertThat(restored.rates()).hasSize(2);
        assertThat(created.rates()).hasSize(1);
    }
}

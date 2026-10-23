package com.ecsite.module.reporting.domain.sales;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.reporting.domain.ReportingErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 期間と区切りの単体テスト（T0207、BR-24、AC-197、201）
 */
class ReportPeriodTest {

    @Nested
    @DisplayName("BR-24 期間")
    class Period {

        @Test
        @DisplayName("始まりが終わりより後なら、期間の誤り")
        void rejectsReversed() {
            assertBusinessError(() -> ReportPeriod.of(date("2026-10-02"), date("2026-10-01")), ReportingErrorCode.INVALID_PERIOD);
        }

        @Test
        @DisplayName("期間は 5 年まで。超えると期間の誤り")
        void rejectsLongerThanFiveYears() {
            assertThat(ReportPeriod.of(date("2021-01-01"), date("2025-12-31")).to()).isEqualTo(date("2025-12-31"));
            assertBusinessError(() -> ReportPeriod.of(date("2021-01-01"), date("2026-01-01")), ReportingErrorCode.INVALID_PERIOD);
        }

        @Test
        @DisplayName("始まりと終わりが同じ日でよい")
        void allowsSingleDay() {
            assertThat(period("2026-10-01", "2026-10-01").split(Granularity.MONTH, 4))
                    .extracting(TrendBucket::start, TrendBucket::end)
                    .containsExactly(tuple(date("2026-10-01"), date("2026-10-01")));
        }
    }

    @Nested
    @DisplayName("BR-24 区切り。期間が区切りの途中で切れたら、期間の中の日だけ")
    class Split {

        @Test
        @DisplayName("週は月曜から日曜")
        void weeks() {
            // 2026-10-01 は木曜、2026-10-14 は水曜
            assertThat(period("2026-10-01", "2026-10-14").split(Granularity.WEEK, 4))
                    .extracting(TrendBucket::start, TrendBucket::end)
                    .containsExactly(
                            tuple(date("2026-10-01"), date("2026-10-04")),
                            tuple(date("2026-10-05"), date("2026-10-11")),
                            tuple(date("2026-10-12"), date("2026-10-14")));
        }

        @Test
        @DisplayName("月は暦の月（うるう年でない 2 月は 28 日まで）")
        void months() {
            assertThat(period("2026-01-15", "2026-03-10").split(Granularity.MONTH, 4))
                    .extracting(TrendBucket::start, TrendBucket::end)
                    .containsExactly(
                            tuple(date("2026-01-15"), date("2026-01-31")),
                            tuple(date("2026-02-01"), date("2026-02-28")),
                            tuple(date("2026-03-01"), date("2026-03-10")));
        }

        @Test
        @DisplayName("四半期は会計年度で区切る（4 月始まり）。1〜3 月は前の年度の第 4 四半期")
        void fiscalQuartersFromApril() {
            assertThat(period("2026-03-01", "2026-10-31").split(Granularity.QUARTER, 4))
                    .extracting(TrendBucket::start, TrendBucket::end, TrendBucket::fiscalYear, TrendBucket::quarter)
                    .containsExactly(
                            tuple(date("2026-03-01"), date("2026-03-31"), 2025, 4),
                            tuple(date("2026-04-01"), date("2026-06-30"), 2026, 1),
                            tuple(date("2026-07-01"), date("2026-09-30"), 2026, 2),
                            tuple(date("2026-10-01"), date("2026-10-31"), 2026, 3));
        }

        @Test
        @DisplayName("四半期は、会計年度が 1 月始まりなら暦の四半期")
        void fiscalQuartersFromJanuary() {
            assertThat(period("2026-01-01", "2026-12-31").split(Granularity.QUARTER, 1))
                    .extracting(TrendBucket::start, TrendBucket::end, TrendBucket::fiscalYear, TrendBucket::quarter)
                    .containsExactly(
                            tuple(date("2026-01-01"), date("2026-03-31"), 2026, 1),
                            tuple(date("2026-04-01"), date("2026-06-30"), 2026, 2),
                            tuple(date("2026-07-01"), date("2026-09-30"), 2026, 3),
                            tuple(date("2026-10-01"), date("2026-12-31"), 2026, 4));
        }

        @Test
        @DisplayName("年は会計年度。年度は始まりの月の年で呼ぶ（2026 年 4 月〜2027 年 3 月は 2026 年度）")
        void fiscalYearsFromApril() {
            assertThat(period("2025-01-01", "2026-12-31").split(Granularity.YEAR, 4))
                    .extracting(TrendBucket::start, TrendBucket::end, TrendBucket::fiscalYear, TrendBucket::quarter)
                    .containsExactly(
                            tuple(date("2025-01-01"), date("2025-03-31"), 2024, null),
                            tuple(date("2025-04-01"), date("2026-03-31"), 2025, null),
                            tuple(date("2026-04-01"), date("2026-12-31"), 2026, null));
        }

        @Test
        @DisplayName("年は、会計年度が 1 月始まりなら暦の年")
        void fiscalYearsFromJanuary() {
            assertThat(period("2025-06-01", "2026-02-01").split(Granularity.YEAR, 1))
                    .extracting(TrendBucket::start, TrendBucket::end, TrendBucket::fiscalYear)
                    .containsExactly(
                            tuple(date("2025-06-01"), date("2025-12-31"), 2025),
                            tuple(date("2026-01-01"), date("2026-02-01"), 2026));
        }

        @Test
        @DisplayName("週と月の区切りは、会計年度を持たない")
        void noFiscalYearForWeekAndMonth() {
            assertThat(period("2026-10-01", "2026-10-31").split(Granularity.MONTH, 4))
                    .extracting(TrendBucket::fiscalYear, TrendBucket::quarter)
                    .containsExactly(tuple(null, null));
        }
    }

    @Nested
    @DisplayName("AC-201 区切りへのまとめ")
    class Sum {

        @Test
        @DisplayName("日ごとの売上を区切りにまとめ、売れなかった区切りは 0 の行にする")
        void sumsAndFillsZero() {
            var buckets = period("2026-01-01", "2026-03-31").split(Granularity.MONTH, 4);
            var daily = Map.of(
                    date("2026-01-05"), new DailySales(Money.of(1000), 1),
                    date("2026-01-31"), new DailySales(Money.of(2500), 3),
                    date("2026-03-01"), new DailySales(Money.of(700), 2));

            assertThat(SalesTrend.sum(buckets, daily))
                    .extracting(point -> point.bucket().start(), TrendPoint::amount, TrendPoint::quantity)
                    .containsExactly(
                            tuple(date("2026-01-01"), Money.of(3500), 4L),
                            tuple(date("2026-02-01"), Money.ZERO, 0L),
                            tuple(date("2026-03-01"), Money.of(700), 2L));
        }
    }

    private static ReportPeriod period(String from, String to) {
        return ReportPeriod.of(date(from), date(to));
    }

    private static LocalDate date(String value) {
        return LocalDate.parse(value);
    }
}

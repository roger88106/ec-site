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
 * 期間と区切りの単体テスト
 *
 * <p>期間が区切りの途中で始まる・終わるときは、端の区切りを期間の中の日だけにする。各粒度のテストは、端で切れる期間で確認する。
 */
class ReportPeriodTest {

    // ============================== 定数 ==============================

    /**
     * 4 月始まりの会計年度の始まりの月
     *
     * <p>設定の既定。
     */
    private static final int APRIL = 4;

    /**
     * 1 月始まりの会計年度の始まりの月
     *
     * <p>会計年度が暦の年と同じになる。
     */
    private static final int JANUARY = 1;

    // ============================== テスト ==============================

    @Nested
    @DisplayName("期間")
    class Period {

        @Test
        @DisplayName("BR-24 始まりが終わりより後なら、期間の誤り")
        void rejectsReversed() {
            // 操作と確認：業務のエラー INVALID_PERIOD で拒否される
            assertBusinessError(() -> ReportPeriod.of(date("2026-10-02"), date("2026-10-01")), ReportingErrorCode.INVALID_PERIOD);
        }

        @Test
        @DisplayName("BR-24 期間は 5 年ちょうどまで作れる")
        void allowsFiveYears() {
            // 操作：始まりから 5 年後の前日までの期間を作る
            var period = ReportPeriod.of(date("2021-01-01"), date("2025-12-31"));

            // 確認：終わりの日がそのまま
            assertThat(period.to()).isEqualTo(date("2025-12-31"));
        }

        @Test
        @DisplayName("BR-24 期間が 5 年を超えると、期間の誤り")
        void rejectsLongerThanFiveYears() {
            // 操作と確認：5 年と 1 日の期間は、業務のエラー INVALID_PERIOD で拒否される
            assertBusinessError(() -> ReportPeriod.of(date("2021-01-01"), date("2026-01-01")), ReportingErrorCode.INVALID_PERIOD);
        }

        @Test
        @DisplayName("BR-24 始まりと終わりが同じ日でよい")
        void allowsSingleDay() {
            // 操作：1 日だけの期間を月で区切る
            var buckets = period("2026-10-01", "2026-10-01").split(Granularity.MONTH, APRIL);

            // 確認：その 1 日だけの区切りが 1 つ
            assertThat(buckets)
                    .extracting(TrendBucket::start, TrendBucket::end)
                    .containsExactly(tuple(date("2026-10-01"), date("2026-10-01")));
        }
    }

    @Nested
    @DisplayName("区切り")
    class Split {

        @Test
        @DisplayName("BR-24 週は月曜から日曜")
        void weeks() {
            // 準備：木曜（2026-10-01）から水曜（2026-10-14）までの期間
            var period = period("2026-10-01", "2026-10-14");

            // 操作：週で区切る
            var buckets = period.split(Granularity.WEEK, APRIL);

            // 確認：月曜から日曜の区切りで、端の週は期間の中の日だけ
            assertThat(buckets)
                    .extracting(TrendBucket::start, TrendBucket::end)
                    .containsExactly(
                            tuple(date("2026-10-01"), date("2026-10-04")),
                            tuple(date("2026-10-05"), date("2026-10-11")),
                            tuple(date("2026-10-12"), date("2026-10-14")));
        }

        @Test
        @DisplayName("BR-24 月は暦の月（うるう年でない 2 月は 28 日まで）")
        void months() {
            // 操作：1 月半ばから 3 月 10 日までを月で区切る
            var buckets = period("2026-01-15", "2026-03-10").split(Granularity.MONTH, APRIL);

            // 確認：暦の月の区切りで、端の月は期間の中の日だけ
            assertThat(buckets)
                    .extracting(TrendBucket::start, TrendBucket::end)
                    .containsExactly(
                            tuple(date("2026-01-15"), date("2026-01-31")),
                            tuple(date("2026-02-01"), date("2026-02-28")),
                            tuple(date("2026-03-01"), date("2026-03-10")));
        }

        @Test
        @DisplayName("BR-24 四半期は会計年度で区切る（4 月始まり）。1〜3 月は前の年度の第 4 四半期")
        void fiscalQuartersFromApril() {
            // 操作：3 月から 10 月までを、4 月始まりの四半期で区切る
            var buckets = period("2026-03-01", "2026-10-31").split(Granularity.QUARTER, APRIL);

            // 確認：3 月は前の年度の第 4 四半期、4 月からは今の年度の第 1 四半期から
            assertThat(buckets)
                    .extracting(TrendBucket::start, TrendBucket::end, TrendBucket::fiscalYear, TrendBucket::quarter)
                    .containsExactly(
                            tuple(date("2026-03-01"), date("2026-03-31"), 2025, 4),
                            tuple(date("2026-04-01"), date("2026-06-30"), 2026, 1),
                            tuple(date("2026-07-01"), date("2026-09-30"), 2026, 2),
                            tuple(date("2026-10-01"), date("2026-10-31"), 2026, 3));
        }

        @Test
        @DisplayName("BR-24 四半期は、会計年度が 1 月始まりなら暦の四半期")
        void fiscalQuartersFromJanuary() {
            // 操作：1 年を、1 月始まりの四半期で区切る
            var buckets = period("2026-01-01", "2026-12-31").split(Granularity.QUARTER, JANUARY);

            // 確認：暦の四半期と同じ
            assertThat(buckets)
                    .extracting(TrendBucket::start, TrendBucket::end, TrendBucket::fiscalYear, TrendBucket::quarter)
                    .containsExactly(
                            tuple(date("2026-01-01"), date("2026-03-31"), 2026, 1),
                            tuple(date("2026-04-01"), date("2026-06-30"), 2026, 2),
                            tuple(date("2026-07-01"), date("2026-09-30"), 2026, 3),
                            tuple(date("2026-10-01"), date("2026-12-31"), 2026, 4));
        }

        @Test
        @DisplayName("BR-24 年は会計年度。年度は始まりの月の年で呼ぶ（2026 年 4 月〜2027 年 3 月は 2026 年度）")
        void fiscalYearsFromApril() {
            // 操作：2 年を、4 月始まりの年で区切る
            var buckets = period("2025-01-01", "2026-12-31").split(Granularity.YEAR, APRIL);

            // 確認：4 月で区切り、年度は始まりの月の年。四半期の番号は持たない
            assertThat(buckets)
                    .extracting(TrendBucket::start, TrendBucket::end, TrendBucket::fiscalYear, TrendBucket::quarter)
                    .containsExactly(
                            tuple(date("2025-01-01"), date("2025-03-31"), 2024, null),
                            tuple(date("2025-04-01"), date("2026-03-31"), 2025, null),
                            tuple(date("2026-04-01"), date("2026-12-31"), 2026, null));
        }

        @Test
        @DisplayName("BR-24 年は、会計年度が 1 月始まりなら暦の年")
        void fiscalYearsFromJanuary() {
            // 操作：年をまたぐ期間を、1 月始まりの年で区切る
            var buckets = period("2025-06-01", "2026-02-01").split(Granularity.YEAR, JANUARY);

            // 確認：1 月で区切り、年度は暦の年
            assertThat(buckets)
                    .extracting(TrendBucket::start, TrendBucket::end, TrendBucket::fiscalYear)
                    .containsExactly(
                            tuple(date("2025-06-01"), date("2025-12-31"), 2025),
                            tuple(date("2026-01-01"), date("2026-02-01"), 2026));
        }

        @Test
        @DisplayName("BR-24 週と月の区切りは、会計年度を持たない")
        void noFiscalYearForWeekAndMonth() {
            // 操作：1 か月を月で区切る
            var buckets = period("2026-10-01", "2026-10-31").split(Granularity.MONTH, APRIL);

            // 確認：会計年度と四半期の番号がない
            assertThat(buckets)
                    .extracting(TrendBucket::fiscalYear, TrendBucket::quarter)
                    .containsExactly(tuple(null, null));
        }
    }

    @Nested
    @DisplayName("区切りへのまとめ")
    class Sum {

        @Test
        @DisplayName("AC-201 日ごとの売上を、その日を含む区切りにまとめ、売れなかった区切りは 0 の行にする")
        void sumsDailySalesIntoBuckets() {
            // 準備：1〜3 月の月の区切りと、1 月に 2 日・3 月に 1 日の売上
            var buckets = period("2026-01-01", "2026-03-31").split(Granularity.MONTH, APRIL);
            var daily = Map.of(
                    date("2026-01-05"), new DailySales(Money.of(1000), 1),
                    date("2026-01-31"), new DailySales(Money.of(2500), 3),
                    date("2026-03-01"), new DailySales(Money.of(700), 2));

            // 操作：区切りにまとめる
            var points = SalesTrend.sum(buckets, daily);

            // 確認：1 月は 2 日の合計、売れなかった 2 月は 0、3 月は 1 日分
            assertThat(points)
                    .extracting(point -> point.bucket().start(), TrendPoint::amount, TrendPoint::quantity)
                    .containsExactly(
                            tuple(date("2026-01-01"), Money.of(3500), 4L),
                            tuple(date("2026-02-01"), Money.ZERO, 0L),
                            tuple(date("2026-03-01"), Money.of(700), 2L));
        }

        @Test
        @DisplayName("AC-201 期間の終わりまで売れなかった区切りが続いても、全ての区切りを 0 の行として並べる")
        void keepsTrailingEmptyBuckets() {
            // 準備：1〜3 月の月の区切りと、1 月だけの売上
            var buckets = period("2026-01-01", "2026-03-31").split(Granularity.MONTH, APRIL);
            var daily = Map.of(date("2026-01-05"), new DailySales(Money.of(1000), 1));

            // 操作：区切りにまとめる
            var points = SalesTrend.sum(buckets, daily);

            // 確認：売れなかった 2 月と 3 月も、0 の行として残る
            assertThat(points)
                    .extracting(point -> point.bucket().start(), TrendPoint::amount, TrendPoint::quantity)
                    .containsExactly(
                            tuple(date("2026-01-01"), Money.of(1000), 1L),
                            tuple(date("2026-02-01"), Money.ZERO, 0L),
                            tuple(date("2026-03-01"), Money.ZERO, 0L));
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 期間を作る。
     *
     * @param from 始まりの日（yyyy-MM-dd）
     * @param to   終わりの日（yyyy-MM-dd）
     * @return 期間
     */
    private static ReportPeriod period(String from, String to) {
        return ReportPeriod.of(date(from), date(to));
    }

    /**
     * 日付を読む。
     *
     * @param value 日付（yyyy-MM-dd）
     * @return 日付
     */
    private static LocalDate date(String value) {
        return LocalDate.parse(value);
    }
}

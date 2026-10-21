package com.ecsite.module.reporting.domain;

import com.ecsite.common.error.exception.BusinessException;
import lombok.NonNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * レポートの期間。始まりと終わりの日（日本時間、両端を含む）。
 *
 * @param from 始まりの日
 * @param to   終わりの日
 */
public record ReportPeriod(LocalDate from, LocalDate to) {

    /**
     * 期間の長さの上限（年）
     */
    public static final int MAX_YEARS = 5;

    /**
     * 期間を作る。
     *
     * @param from 始まりの日
     * @param to   終わりの日
     * @return 期間
     * @throws BusinessException 始まりが終わりより後、または {@value #MAX_YEARS} 年を超える
     */
    public static ReportPeriod of(@NonNull LocalDate from, @NonNull LocalDate to) {
        if (from.isAfter(to) || !to.isBefore(from.plusYears(MAX_YEARS))) {
            throw BusinessException.of(ReportingErrorCode.INVALID_PERIOD, List.of(MAX_YEARS));
        }
        return new ReportPeriod(from, to);
    }

    /**
     * 期間を区切りに分ける。期間が区切りの途中で切れたら、その区切りは期間の中の日だけにする。
     *
     * @param granularity          粒度
     * @param fiscalYearStartMonth 会計年度の始まりの月（1〜12）
     * @return 区切り（日の順）
     */
    public List<TrendBucket> split(Granularity granularity, int fiscalYearStartMonth) {
        var buckets = new ArrayList<TrendBucket>();
        var cursor = from;
        while (!cursor.isAfter(to)) {
            var whole = bucketOf(cursor, granularity, fiscalYearStartMonth);
            var end = whole.end().isAfter(to) ? to : whole.end();
            buckets.add(new TrendBucket(cursor, end, whole.fiscalYear(), whole.quarter()));
            cursor = end.plusDays(1);
        }
        return buckets;
    }

    /**
     * 日を含む、切る前の区切りを返す。
     */
    private static TrendBucket bucketOf(LocalDate date, Granularity granularity, int fiscalYearStartMonth) {
        return switch (granularity) {
            case WEEK -> {
                var start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield new TrendBucket(start, start.plusDays(6), null, null);
            }
            case MONTH -> new TrendBucket(date.withDayOfMonth(1), date.with(TemporalAdjusters.lastDayOfMonth()), null, null);
            case QUARTER -> {
                var fiscalYear = fiscalYearOf(date, fiscalYearStartMonth);
                var yearStart = LocalDate.of(fiscalYear, fiscalYearStartMonth, 1);
                int months = (date.getYear() * 12 + date.getMonthValue()) - (fiscalYear * 12 + fiscalYearStartMonth);
                int quarter = months / 3 + 1;
                var start = yearStart.plusMonths((quarter - 1) * 3L);
                yield new TrendBucket(start, start.plusMonths(3).minusDays(1), fiscalYear, quarter);
            }
            case YEAR -> {
                var fiscalYear = fiscalYearOf(date, fiscalYearStartMonth);
                var start = LocalDate.of(fiscalYear, fiscalYearStartMonth, 1);
                yield new TrendBucket(start, start.plusYears(1).minusDays(1), fiscalYear, null);
            }
        };
    }

    /**
     * 日の会計年度（始まりの月の年）。始まりの月より前の月は、前の年の年度。
     */
    private static int fiscalYearOf(LocalDate date, int fiscalYearStartMonth) {
        return date.getMonthValue() >= fiscalYearStartMonth ? date.getYear() : date.getYear() - 1;
    }
}

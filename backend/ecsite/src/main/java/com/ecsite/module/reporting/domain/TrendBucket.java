package com.ecsite.module.reporting.domain;

import jakarta.annotation.Nullable;

import java.time.LocalDate;

/**
 * 売上の推移の 1 つの区切り。始まりと終わりは、期間で切った日（両端を含む）。
 *
 * @param start      始まりの日
 * @param end        終わりの日
 * @param fiscalYear 会計年度（始まりの月の年）。四半期と年のときだけ
 * @param quarter    会計年度の四半期（1〜4）。四半期のときだけ
 */
public record TrendBucket(LocalDate start, LocalDate end, @Nullable Integer fiscalYear, @Nullable Integer quarter) {

    /**
     * 日がこの区切りに入るかを返す。
     *
     * @param date 日
     * @return 日がこの区切りに入れば true
     */
    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }
}

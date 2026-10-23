package com.ecsite.module.reporting.domain.sales;

/**
 * 集計の粒度（売上の推移の区切り）
 */
public enum Granularity {

    /**
     * 週（月曜から日曜）
     */
    WEEK,

    /**
     * 月（暦の月）
     */
    MONTH,

    /**
     * 四半期（会計年度の 3 か月ごと）
     */
    QUARTER,

    /**
     * 年（会計年度）
     */
    YEAR,
}

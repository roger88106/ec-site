package com.ecsite.module.reporting.domain;

/**
 * 注文時の性別（注文の写しに持つ値）。集計の区分（{@link ReportGender}）と違い、「回答しない」を分けて持つ。
 */
public enum ReportBuyerGender {

    /**
     * 男性
     */
    MALE,

    /**
     * 女性
     */
    FEMALE,

    /**
     * その他
     */
    OTHER,

    /**
     * 回答しない
     */
    UNDISCLOSED,
}

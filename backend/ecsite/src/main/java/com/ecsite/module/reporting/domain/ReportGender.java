package com.ecsite.module.reporting.domain;

import jakarta.annotation.Nullable;

/**
 * レポートの性別の区分（BR-29）。注文に記録した性別のうち、「回答しない」と記録のないものを不明にまとめる。
 */
public enum ReportGender {

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
     * 不明（回答しない、または記録がない）
     */
    UNKNOWN,
    ;

    /**
     * 注文に記録した性別の区分を返す。
     *
     * @param gender 注文時の性別（性別の名前）。記録がなければ null
     * @return 区分
     */
    public static ReportGender of(@Nullable String gender) {
        if (gender == null) {
            return UNKNOWN;
        }
        return switch (gender) {
            case "MALE" -> MALE;
            case "FEMALE" -> FEMALE;
            case "OTHER" -> OTHER;
            default -> UNKNOWN;
        };
    }
}

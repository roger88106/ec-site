package com.ecsite.module.reporting.domain;

import jakarta.annotation.Nullable;

/**
 * 年齢層。注文時の年齢から、問い合わせのときに求める。並びは若い順で、不明が最後。
 */
public enum AgeGroup {

    /**
     * 10 代以下
     */
    TEENS_OR_YOUNGER,

    /**
     * 20 代
     */
    TWENTIES,

    /**
     * 30 代
     */
    THIRTIES,

    /**
     * 40 代
     */
    FORTIES,

    /**
     * 50 代
     */
    FIFTIES,

    /**
     * 60 代以上
     */
    SIXTIES_OR_OLDER,

    /**
     * 不明（年齢を記録していない）
     */
    UNKNOWN,
    ;

    /**
     * 年齢の年齢層を返す。
     *
     * @param age 注文時の年齢。分からなければ null
     * @return 年齢層
     */
    public static AgeGroup of(@Nullable Integer age) {
        if (age == null) {
            return UNKNOWN;
        }
        if (age < 20) {
            return TEENS_OR_YOUNGER;
        }
        if (age >= 60) {
            return SIXTIES_OR_OLDER;
        }
        return values()[age / 10 - 1];
    }
}

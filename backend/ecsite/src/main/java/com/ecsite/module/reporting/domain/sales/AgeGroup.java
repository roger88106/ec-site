package com.ecsite.module.reporting.domain.sales;

import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

/**
 * 年齢層
 *
 * <p>注文時の年齢から求める。並びは若い順で、不明が最後。年齢は 10 歳ごとに分け、19 歳以下と 60 歳以上はそれぞれ 1 つにまとめる。
 * 各要素は下限の年齢を持ち、年齢が下限以上の要素のうち、下限が最大のものになる。
 */
public enum AgeGroup {

    /**
     * 10 代以下
     */
    TEENS_OR_YOUNGER(0),

    /**
     * 20 代
     */
    TWENTIES(20),

    /**
     * 30 代
     */
    THIRTIES(30),

    /**
     * 40 代
     */
    FORTIES(40),

    /**
     * 50 代
     */
    FIFTIES(50),

    /**
     * 60 代以上
     */
    SIXTIES_OR_OLDER(60),

    /**
     * 不明（年齢を記録していない）
     */
    UNKNOWN(null),
    ;

    /**
     * 下限の年齢
     *
     * <p>この年齢以上の年齢が、この年齢層になる。不明は null。
     */
    private final @Nullable Integer minAge;

    /**
     * @param minAge 下限の年齢。不明なら null
     */
    AgeGroup(@Nullable Integer minAge) {
        this.minAge = minAge;
    }

    /**
     * 下限の年齢を返す。
     *
     * @return 下限の年齢。不明なら null
     */
    @Nullable Integer minAge() {
        return minAge;
    }

    /**
     * 年齢の年齢層を返す。
     *
     * @param age 注文時の年齢。分からなければ null
     * @return 年齢層。負の年齢は 10 代以下
     */
    public static AgeGroup of(@Nullable Integer age) {
        if (age == null) {
            return UNKNOWN;
        }
        // 下限が年齢以下の要素のうち、下限が最大のもの。宣言の順には頼らない
        return Arrays.stream(values())
                .filter(group -> group.minAge != null && group.minAge <= age)
                .max(Comparator.comparingInt(group -> Objects.requireNonNull(group.minAge)))
                .orElse(TEENS_OR_YOUNGER);
    }
}

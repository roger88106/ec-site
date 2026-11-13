package com.ecsite.module.order.domain.order.vo;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/**
 * 注文時の性別
 *
 * <p>会員が IdP で選んだ値の写し。「回答しない」は不明（記録しない）と分けて持つ。
 */
@RequiredArgsConstructor
public enum Gender {

    /**
     * 男性
     */
    MALE("male"),

    /**
     * 女性
     */
    FEMALE("female"),

    /**
     * その他
     */
    OTHER("other"),

    /**
     * 回答しない
     */
    UNDISCLOSED("undisclosed"),
    ;

    /**
     * IdP の属性の値
     */
    @NonNull
    private final String claim;

    /**
     * IdP の属性の値から性別を求める。
     *
     * @param claim token の値
     * @return 性別。値がない・一覧にない値なら null（不明）
     */
    public static @Nullable Gender fromClaim(@Nullable String claim) {
        return Arrays.stream(values()).filter(gender -> gender.claim.equals(claim)).findFirst().orElse(null);
    }
}

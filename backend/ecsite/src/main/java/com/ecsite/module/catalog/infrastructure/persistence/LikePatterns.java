package com.ecsite.module.catalog.infrastructure.persistence;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * LIKE の検索パターンを作る。<br />
 * 利用者が入力した文字列の中のワイルドカード文字（{@code %}、{@code _}）を、ただの文字として扱うためにエスケープする。
 * エスケープ文字は {@link #ESCAPE}。jOOQ では {@code field.like(pattern, LikePatterns.ESCAPE)} のように使う。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class LikePatterns {

    /**
     * エスケープ文字
     */
    static final char ESCAPE = '!';

    /**
     * 前方一致のパターンを作る。
     *
     * @param value 利用者が入力した文字列
     * @return {@code value%}（ワイルドカード文字はエスケープ済み）
     */
    static String startsWith(String value) {
        return escape(value) + "%";
    }

    /**
     * 部分一致のパターンを作る。
     *
     * @param value 利用者が入力した文字列
     * @return {@code %value%}（ワイルドカード文字はエスケープ済み）
     */
    static String contains(String value) {
        return "%" + escape(value) + "%";
    }

    /**
     * エスケープ文字とワイルドカード文字をエスケープする。
     *
     * @param value 文字列
     * @return エスケープした文字列
     */
    static String escape(String value) {
        return value
                .replace(String.valueOf(ESCAPE), "" + ESCAPE + ESCAPE)
                .replace("%", ESCAPE + "%")
                .replace("_", ESCAPE + "_");
    }
}

package com.ecsite.common.persistence;

/**
 * LIKE の検索パターンを作る。<br />
 * 利用者が入力した文字列の中のワイルドカード文字（{@code %}、{@code _}）を、ただの文字として扱うためにエスケープする。
 * エスケープ文字は {@link #ESCAPE}。jOOQ では {@code field.like(pattern, LikePatterns.ESCAPE)} のように使う。
 */
public final class LikePatterns {

    /**
     * エスケープ文字
     */
    public static final char ESCAPE = '!';

    private LikePatterns() {
    }

    /**
     * 前方一致のパターンを作る。
     *
     * @param value 利用者が入力した文字列
     * @return {@code value%}（ワイルドカード文字はエスケープ済み）
     */
    public static String startsWith(String value) {
        return escape(value) + "%";
    }

    /**
     * 部分一致のパターンを作る。
     *
     * @param value 利用者が入力した文字列
     * @return {@code %value%}（ワイルドカード文字はエスケープ済み）
     */
    public static String contains(String value) {
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

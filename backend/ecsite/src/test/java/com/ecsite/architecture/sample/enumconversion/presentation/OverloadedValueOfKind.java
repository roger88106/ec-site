package com.ecsite.architecture.sample.enumconversion.presentation;

/**
 * 列挙型の変換の検査の例：presentation の列挙型が、{@code valueOf} という名前の多重定義の中で、名前の文字列から変換する（規則に合わない）
 *
 * <p>コンパイラが作る {@code valueOf(String)} と、名前が同じだけの手書きのメソッドを区別できることを確認するための例であり、
 * 本番のコードではない。
 */
public enum OverloadedValueOfKind {

    /**
     * 1 つ目の値
     */
    FIRST;

    /**
     * コードと名前の文字列から変換する。
     *
     * @param code コード（使わない）
     * @param name 名前の文字列
     * @return 列挙の値
     */
    static OverloadedValueOfKind valueOf(int code, String name) {
        return Enum.valueOf(OverloadedValueOfKind.class, name);
    }
}

package com.ecsite.architecture.sample.enumconversion.presentation;

import com.ecsite.architecture.sample.enumconversion.SampleKind;

import java.util.function.Function;

/**
 * 列挙型の変換の検査の例：domain と persistence の外で、名前の文字列から列挙型に変換する（規則に合わない）
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public final class ConvertsEnumFromString {

    /**
     * インスタンスを作らない。
     */
    private ConvertsEnumFromString() {
    }

    /**
     * valueOf で変換する。
     *
     * @param name 名前の文字列
     * @return 列挙の値
     */
    static SampleKind byValueOf(String name) {
        return SampleKind.valueOf(name);
    }

    /**
     * Enum.valueOf(Class, String) で変換する。
     *
     * @param name 名前の文字列
     * @return 列挙の値
     */
    static SampleKind byEnumValueOf(String name) {
        return Enum.valueOf(SampleKind.class, name);
    }

    /**
     * fromValue をメソッド参照で渡す。
     *
     * @return 変換する関数
     */
    static Function<String, SampleKind> byFromValue() {
        return SampleKind::fromValue;
    }

    /**
     * 列挙でない型の valueOf は当たらない。
     *
     * @param text 数字の文字列
     * @return 数
     */
    static Integer notEnum(String text) {
        return Integer.valueOf(text);
    }
}

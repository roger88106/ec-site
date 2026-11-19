package com.ecsite.architecture.sample.enumconversion.presentation;

import com.ecsite.architecture.sample.enumconversion.SampleKind;

/**
 * 列挙型の変換の検査の例：presentation で、列挙型の名前で修飾した Enum.valueOf(Class, String) を呼ぶ（規則に合わない）
 *
 * <p>クラスファイルでは、呼ぶ先の所有者が修飾の型（列挙型）になるため、別のクラスに分けて確認する。
 * 構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public final class QualifiedEnumValueOfKind {

    /**
     * インスタンスを作らない。
     */
    private QualifiedEnumValueOfKind() {
    }

    /**
     * 列挙型で修飾した Enum.valueOf(Class, String) で変換する。
     *
     * @param name 名前の文字列
     * @return 列挙の値
     */
    static SampleKind byQualifiedValueOf(String name) {
        return SampleKind.valueOf(SampleKind.class, name);
    }
}

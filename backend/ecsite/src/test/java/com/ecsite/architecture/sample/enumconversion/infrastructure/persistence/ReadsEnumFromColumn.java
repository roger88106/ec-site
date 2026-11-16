package com.ecsite.architecture.sample.enumconversion.infrastructure.persistence;

import com.ecsite.architecture.sample.enumconversion.SampleKind;

/**
 * 列挙型の変換の検査の例：永続化で、DB の文字列から列挙型に変換する（規則に合う）
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public final class ReadsEnumFromColumn {

    /**
     * インスタンスを作らない。
     */
    private ReadsEnumFromColumn() {
    }

    /**
     * DB の列の文字列から変換する。
     *
     * @param column 列の値
     * @return 列挙の値
     */
    static SampleKind read(String column) {
        return SampleKind.valueOf(column);
    }
}

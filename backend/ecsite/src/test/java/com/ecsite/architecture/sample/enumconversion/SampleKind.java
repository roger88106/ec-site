package com.ecsite.architecture.sample.enumconversion;

/**
 * 列挙型の変換の検査の例で、変換する列挙型
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public enum SampleKind {

    /**
     * 1 つ目の値
     */
    FIRST;

    /**
     * 名前の文字列から変換する（生成したコードの列挙型が持つ操作と同じ形）。
     *
     * @param value 名前の文字列
     * @return 列挙の値
     */
    public static SampleKind fromValue(String value) {
        return valueOf(value);
    }
}

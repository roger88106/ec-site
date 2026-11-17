package com.ecsite.architecture.sample.staticonly;

/**
 * インスタンスを作らないクラスの検査の例：static でない項目を持つ（対象でない）
 *
 * <p>構成の検査が規則に合う例を違反にしないことを確認するための例であり、本番のコードではない。
 */
public final class WithInstanceField {

    /**
     * 例の項目
     */
    int value;
}

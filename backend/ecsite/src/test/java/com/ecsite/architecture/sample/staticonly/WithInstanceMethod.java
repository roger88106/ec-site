package com.ecsite.architecture.sample.staticonly;

/**
 * インスタンスを作らないクラスの検査の例：static でないメソッドを持つ（対象でない）
 *
 * <p>構成の検査が規則に合う例を違反にしないことを確認するための例であり、本番のコードではない。
 */
public final class WithInstanceMethod {

    /**
     * 例のメソッド。
     *
     * @return 1
     */
    int one() {
        return 1;
    }
}

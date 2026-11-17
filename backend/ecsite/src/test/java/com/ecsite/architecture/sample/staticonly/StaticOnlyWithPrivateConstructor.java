package com.ecsite.architecture.sample.staticonly;

/**
 * インスタンスを作らないクラスの検査の例：static のメンバーだけで、コンストラクタが private（規則に合う）
 *
 * <p>構成の検査が規則に合う例を違反にしないことを確認するための例であり、本番のコードではない。
 */
public final class StaticOnlyWithPrivateConstructor {

    /**
     * インスタンスを作らない。
     */
    private StaticOnlyWithPrivateConstructor() {
    }

    /**
     * 例の関数。
     *
     * @return 1
     */
    static int one() {
        return 1;
    }
}

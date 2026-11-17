package com.ecsite.architecture.sample.staticonly;

/**
 * インスタンスを作らないクラスの検査の例：static のメンバーだけで、コンストラクタが public（規則に合わない）
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public final class StaticOnlyWithPublicConstructor {

    /**
     * 例の定数
     */
    static final int LIMIT = 1;

    /**
     * 例のコンストラクタ（規則に合わない）
     */
    public StaticOnlyWithPublicConstructor() {
    }

    /**
     * 例の関数。
     *
     * @return 定数
     */
    static int limit() {
        return LIMIT;
    }
}

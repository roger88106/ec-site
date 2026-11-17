package com.ecsite.architecture.sample.staticonly;

/**
 * インスタンスを作らないクラスの検査の例：static のメンバーだけで、コンストラクタは private だが final でない（final の規則に合わない）
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public class StaticOnlyNotFinal {

    /**
     * インスタンスを作らない。
     */
    private StaticOnlyNotFinal() {
    }

    /**
     * 例の関数。
     *
     * @return 2
     */
    static int two() {
        return 2;
    }
}

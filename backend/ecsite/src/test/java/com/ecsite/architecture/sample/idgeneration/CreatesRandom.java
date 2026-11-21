package com.ecsite.architecture.sample.idgeneration;

import java.util.Random;

/**
 * 識別子と乱数の生成の検査の例：new Random() を書く（規則に合わない）
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public final class CreatesRandom {

    /**
     * 例の関数。
     *
     * @return 乱数
     */
    // 構成の検査の違反の例。規則が見つける呼び出しを、そのまま書く
    @SuppressWarnings("RandomModInteger")
    Random create() {
        return new Random();
    }
}

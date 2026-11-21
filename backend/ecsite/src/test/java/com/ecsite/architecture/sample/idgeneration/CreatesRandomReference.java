package com.ecsite.architecture.sample.idgeneration;

import java.util.Random;
import java.util.function.Supplier;

/**
 * 識別子と乱数の生成の検査の例：Random::new をコンストラクター参照で使う（規則に合わない）
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public final class CreatesRandomReference {

    /**
     * 例の関数。
     *
     * @return 乱数を作る関数
     */
    Supplier<Random> factory() {
        return Random::new;
    }
}

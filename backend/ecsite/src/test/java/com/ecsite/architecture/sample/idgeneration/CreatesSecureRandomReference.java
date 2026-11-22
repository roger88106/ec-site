package com.ecsite.architecture.sample.idgeneration;

import java.security.SecureRandom;
import java.util.function.Supplier;

/**
 * 識別子と乱数の生成の検査の例：SecureRandom::new をコンストラクター参照で使う（規則に合わない）
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public final class CreatesSecureRandomReference {

    /**
     * 例の関数。
     *
     * @return 乱数を作る関数
     */
    Supplier<SecureRandom> factory() {
        return SecureRandom::new;
    }
}

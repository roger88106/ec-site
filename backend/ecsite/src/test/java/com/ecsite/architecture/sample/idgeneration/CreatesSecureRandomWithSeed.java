package com.ecsite.architecture.sample.idgeneration;

import java.security.SecureRandom;

/**
 * 識別子と乱数の生成の検査の例：new SecureRandom(byte[]) を書く（規則に合わない）
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public final class CreatesSecureRandomWithSeed {

    /**
     * 例の関数。
     *
     * @return 乱数
     */
    SecureRandom create() {
        return new SecureRandom(new byte[] {1});
    }
}

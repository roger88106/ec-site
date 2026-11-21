package com.ecsite.architecture.sample.idgeneration;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 識別子と乱数の生成の検査の例：UUID::randomUUID をメソッド参照で使う（規則に合わない）
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public final class CallsRandomUuidReference {

    /**
     * 例の関数。
     *
     * @return 識別子を作る関数
     */
    Supplier<UUID> factory() {
        return UUID::randomUUID;
    }
}

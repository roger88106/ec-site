package com.ecsite.architecture.sample.idgeneration;

import java.util.UUID;

/**
 * 識別子と乱数の生成の検査の例：UUID.randomUUID() を呼ぶ（規則に合わない）
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public final class CallsRandomUuid {

    /**
     * 例の関数。
     *
     * @return 識別子
     */
    UUID create() {
        return UUID.randomUUID();
    }
}

package com.ecsite.architecture.sample.currenttime;

import java.time.Instant;

/**
 * 現在時刻の取り方の検査の例：引数なしの now() を呼ぶ（規則に合わない）
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public final class CallsSystemNow {

    /**
     * 例の関数。
     *
     * @return 今の時刻
     */
    Instant now() {
        return Instant.now();
    }
}

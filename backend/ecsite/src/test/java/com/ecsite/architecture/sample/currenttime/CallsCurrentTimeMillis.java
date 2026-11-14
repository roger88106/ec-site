package com.ecsite.architecture.sample.currenttime;

/**
 * 現在時刻の取り方の検査の例：System.currentTimeMillis() を呼ぶ（規則に合わない）
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public final class CallsCurrentTimeMillis {

    /**
     * 例の関数。
     *
     * @return 今の時刻
     */
    long now() {
        return System.currentTimeMillis();
    }
}

package com.ecsite.architecture.sample.currenttime;

import java.time.Clock;

/**
 * 現在時刻の取り方の検査の例：Clock.systemDefaultZone() で時計を作る（規則に合わない）
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public final class CreatesDefaultZoneClock {

    /**
     * 例の関数。
     *
     * @return 今の時刻
     */
    // 構成の検査の違反の例。規則が見つける呼び出しを、そのまま書く
    @SuppressWarnings("JavaTimeDefaultTimeZone")
    Clock now() {
        return Clock.systemDefaultZone();
    }
}

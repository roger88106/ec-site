package com.ecsite.architecture.sample.currenttime;

import java.time.Clock;
import java.time.Instant;

/**
 * 現在時刻の取り方の検査の例：注入した時計を渡して now を呼ぶ（規則に合う）
 *
 * <p>構成の検査が規則に合う例を違反にしないことを確認するための例であり、本番のコードではない。
 */
public final class CallsNowWithClock {

    /**
     * 注入した時計
     */
    private final Clock clock;

    /**
     * 例を作る。
     *
     * @param clock 注入した時計
     */
    public CallsNowWithClock(Clock clock) {
        this.clock = clock;
    }

    /**
     * 例の関数。
     *
     * @return 今の時刻
     */
    Instant now() {
        return Instant.now(clock);
    }
}

package com.ecsite.architecture.sample.testcode;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

/**
 * 待ち方の検査の例：条件で待つ（規則に合う）
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public final class WaitsForCondition {

    /**
     * インスタンスを作らない。
     */
    private WaitsForCondition() {
    }

    /**
     * 条件が成り立つまで待つ。上限の時間は、条件が成り立たないときに止めるためのもの。
     *
     * @param latch 待つ合図
     * @return 合図が来れば true
     * @throws InterruptedException 待つ間に割り込まれたとき
     */
    static boolean waits(CountDownLatch latch) throws InterruptedException {
        await().atMost(Duration.ofSeconds(1)).until(() -> true);
        return latch.await(1, TimeUnit.SECONDS);
    }
}

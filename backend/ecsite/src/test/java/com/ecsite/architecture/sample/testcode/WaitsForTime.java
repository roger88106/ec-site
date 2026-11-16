package com.ecsite.architecture.sample.testcode;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

/**
 * 待ち方の検査の例：時間で待つ（規則に合わない）
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public final class WaitsForTime {

    /**
     * インスタンスを作らない。
     */
    private WaitsForTime() {
    }

    /**
     * スレッドを止めて待つ。
     *
     * @throws InterruptedException 待つ間に割り込まれたとき
     */
    static void sleeps() throws InterruptedException {
        Thread.sleep(10);
        TimeUnit.MILLISECONDS.sleep(10);
    }

    /**
     * 決まった時間だけ見る、待ってから見る。
     */
    static void waitsWithAwaitility() {
        await().during(Duration.ofMillis(10)).until(() -> true);
        await().pollDelay(Duration.ofMillis(10)).until(() -> true);
    }
}

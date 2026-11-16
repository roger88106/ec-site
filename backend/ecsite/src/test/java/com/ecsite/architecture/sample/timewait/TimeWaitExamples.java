package com.ecsite.architecture.sample.timewait;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

/**
 * 時間の待ちの検査の例
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、テストではない。
 */
public final class TimeWaitExamples {

    /**
     * インスタンスを作らない。
     */
    private TimeWaitExamples() {
    }

    /**
     * Thread.sleep で待つ（規則に合わない）
     */
    public static final class SleepsThread {

        /**
         * 例の関数。
         *
         * @throws InterruptedException 待ちが中断されたとき
         */
        void waitFor() throws InterruptedException {
            Thread.sleep(10);
        }
    }

    /**
     * TimeUnit の sleep で待つ（規則に合わない）
     */
    public static final class SleepsTimeUnit {

        /**
         * 例の関数。
         *
         * @throws InterruptedException 待ちが中断されたとき
         */
        void waitFor() throws InterruptedException {
            TimeUnit.MILLISECONDS.sleep(10);
        }
    }

    /**
     * Awaitility の pollDelay で、決まった時間だけ待ってから見る（規則に合わない）
     */
    public static final class PollsWithDelay {

        /**
         * 例の関数。
         */
        void waitFor() {
            await().pollDelay(Duration.ofMillis(10)).until(() -> true);
        }
    }

    /**
     * Awaitility の during で、決まった時間だけ見続ける（規則に合わない）
     */
    public static final class WaitsDuring {

        /**
         * 例の関数。
         */
        void waitFor() {
            await().during(Duration.ofMillis(10)).until(() -> true);
        }
    }

    /**
     * Awaitility で条件を満たすまで待つ（規則に合う）
     */
    public static final class WaitsForCondition {

        /**
         * 例の関数。
         */
        void waitFor() {
            await().atMost(Duration.ofSeconds(1)).until(() -> true);
        }
    }
}

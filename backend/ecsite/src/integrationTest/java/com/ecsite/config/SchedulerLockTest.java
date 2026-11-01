package com.ecsite.config;

import com.ecsite.IntegrationTest;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 定期ジョブの分散ロックの統合テスト（バックエンドのアーキテクチャの「水平スケール」）。<br />
 * 別々のインスタンスの代わりに 2 つのスレッドから同じジョブを同時に呼び、DB のロックで 1 回だけ動くことを確認する。
 */
@IntegrationTest
class SchedulerLockTest {

    @Autowired
    LockedJob job;

    @Test
    @DisplayName("同じジョブが動いている間にもう 1 回起動しても、飛ばされ、1 回だけ動く")
    void runsOnlyOnceAtTheSameTime() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = CompletableFuture.runAsync(job::run, executor);
            job.awaitRunning();

            job.run();
            job.finish();
            first.get(30, TimeUnit.SECONDS);
        }

        assertThat(job.runs()).isEqualTo(1);
    }

    /**
     * テストのジョブを登録する。
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        LockedJob lockedJob() {
            return new LockedJob();
        }
    }

    /**
     * 動いた回数を数え、テストが終わらせるまで終わらないジョブ（時間で待たずに、2 回目を 1 回目の最中に呼ぶ）。
     */
    static class LockedJob {

        /**
         * 動いた回数
         */
        private final AtomicInteger runs = new AtomicInteger();

        /**
         * 1 回目が動き始めた合図
         */
        private final CountDownLatch running = new CountDownLatch(1);

        /**
         * 1 回目を終わらせる合図
         */
        private final CountDownLatch finish = new CountDownLatch(1);

        /**
         * ジョブの本体
         */
        @SchedulerLock(name = "scheduler-lock-test", lockAtMostFor = "PT1M")
        public void run() {
            runs.incrementAndGet();
            running.countDown();
            try {
                finish.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        /**
         * 1 回目が動き始めるのを待つ。
         */
        void awaitRunning() throws InterruptedException {
            assertThat(running.await(30, TimeUnit.SECONDS)).isTrue();
        }

        /**
         * 1 回目を終わらせる。
         */
        void finish() {
            finish.countDown();
        }

        /**
         * @return 動いた回数
         */
        int runs() {
            return runs.get();
        }
    }
}

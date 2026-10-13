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
 * 定期ジョブの分散ロックの統合テスト（T7103、バックエンドのアーキテクチャ 第 5 節）。<br />
 * 別々のインスタンスの代わりに 2 つのスレッドから同じジョブを同時に呼び、DB のロックで 1 回だけ動くことを確かめる。
 */
@IntegrationTest
class SchedulerLockTest {

    @Autowired
    LockedJob job;

    @Test
    @DisplayName("同じジョブを同時に 2 回起動しても、1 回だけ動き、もう 1 回は飛ばされる")
    void runsOnlyOnceAtTheSameTime() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = CompletableFuture.runAsync(() -> runAfter(start), executor);
            var second = CompletableFuture.runAsync(() -> runAfter(start), executor);
            start.countDown();
            CompletableFuture.allOf(first, second).get(30, TimeUnit.SECONDS);
        }

        assertThat(job.runs()).isEqualTo(1);
    }

    /**
     * 合図を待ってからジョブを呼ぶ。
     */
    private void runAfter(CountDownLatch start) {
        try {
            start.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        job.run();
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
     * 動いた回数を数え、もう一方が同時に呼ばれるよう、しばらく終わらないジョブ。
     */
    static class LockedJob {

        /**
         * 動いた回数
         */
        private final AtomicInteger runs = new AtomicInteger();

        /**
         * ジョブの本体
         */
        @SchedulerLock(name = "scheduler-lock-test", lockAtMostFor = "PT1M")
        public void run() {
            runs.incrementAndGet();
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        /**
         * @return 動いた回数
         */
        int runs() {
            return runs.get();
        }
    }
}

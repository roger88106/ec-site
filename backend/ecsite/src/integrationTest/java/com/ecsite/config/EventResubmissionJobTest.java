package com.ecsite.config;

import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.TestClockConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ecsite.AsyncEvents.TIMEOUT;
import static org.awaitility.Awaitility.await;

/**
 * 未完了のイベントの再発行の統合テスト（T7104、バックエンドのアーキテクチャ 第 5 節）。<br />
 * 1 回目だけ失敗するリスナーで未完了のイベントを作り、時計を進めてから再発行のジョブを呼ぶ。
 * イベントはコミット後に非同期で処理されるので、処理を待つ。
 */
@IntegrationTest
@Import(TestClockConfig.class)
class EventResubmissionJobTest {

    /**
     * テストの現在時刻（イベントの発行日時になる）
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    @Autowired
    EventResubmissionJob job;

    @Autowired
    FlakyListener listener;

    @Autowired
    MutableClock clock;

    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Autowired
    TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        clock.setInstant(NOW);
    }

    @Test
    @DisplayName("処理に失敗したイベントは、5 分たつと再発行され、リスナーで処理される")
    void resubmitsFailedEventAfterMinAge() {
        var event = failedOnce();

        clock.advance(Duration.ofMinutes(6));
        job.resubmit();

        await().atMost(TIMEOUT).until(() -> listener.attempts(event) == 2 && listener.succeeded(event));
    }

    @Test
    @DisplayName("処理に失敗してから 5 分たっていないイベントは、再発行しない")
    void doesNotResubmitRecentEvent() {
        var event = failedOnce();

        clock.advance(Duration.ofMinutes(4));
        job.resubmit();

        await().during(Duration.ofMillis(500)).atMost(TIMEOUT).until(() -> listener.attempts(event) == 1);
    }

    /**
     * イベントをコミットするトランザクションで発行し、リスナーの 1 回目の失敗を待つ。
     */
    private FlakyEvent failedOnce() {
        var event = new FlakyEvent(UUID.randomUUID());
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
        await().atMost(TIMEOUT).until(() -> listener.attempts(event) == 1);
        return event;
    }

    /**
     * テストのイベント
     *
     * @param id イベントを見分ける ID
     */
    record FlakyEvent(UUID id) {
    }

    /**
     * テストのリスナーを登録する。
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        FlakyListener flakyListener() {
            return new FlakyListener();
        }
    }

    /**
     * イベントごとに、1 回目だけ失敗するリスナー。
     */
    static class FlakyListener {

        /**
         * イベント ID → 受けた回数
         */
        private final Map<UUID, AtomicInteger> attempts = new ConcurrentHashMap<>();

        /**
         * 処理に成功したイベント ID
         */
        private final Map<UUID, Boolean> succeeded = new ConcurrentHashMap<>();

        /**
         * 1 回目は例外で終わり、イベントを未完了のまま残す。
         *
         * @param event テストのイベント
         */
        @ApplicationModuleListener
        void on(FlakyEvent event) {
            var count = attempts.computeIfAbsent(event.id(), id -> new AtomicInteger()).incrementAndGet();
            if (count == 1) {
                throw new IllegalStateException("1 回目は失敗させる");
            }
            succeeded.put(event.id(), true);
        }

        int attempts(FlakyEvent event) {
            var count = attempts.get(event.id());
            return count == null ? 0 : count.get();
        }

        boolean succeeded(FlakyEvent event) {
            return succeeded.containsKey(event.id());
        }
    }
}

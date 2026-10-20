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
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.modulith.events.core.TargetEventPublication;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ecsite.AsyncEvents.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 未完了のイベントの再発行の統合テスト（T7104、T017-02-01、バックエンドのアーキテクチャ 第 5 節）。<br />
 * 1 回目だけ失敗するリスナーと、いつも失敗するリスナーで未完了のイベントを作り、時計を進めてから再発行のジョブを呼ぶ。
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
    FailingListener failingListener;

    @Autowired
    IncompleteEventPublications incompletePublications;

    @Autowired
    MutableClock clock;

    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    EventPublicationRegistry registry;

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

        // 再発行すると、発行の記録の再発行の日時（初めは発行の日時）が変わる。リスナーは非同期で動くので、受けた回数だけでは確かめられない
        var publication = publicationOf(event);
        assertThat(publication.getLastResubmissionDate()).isEqualTo(publication.getPublicationDate());
        assertThat(listener.attempts(event)).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-290 処理に失敗し続けるイベントは、処理の回数が上限に達したら、それ以上再発行しない")
    // 処理の回数（completion_attempts）は、発行の時点で 1、再発行するたびに 1 つ増える（Spring Modulith 2.1 の動き）。
    // 上限 3 なら、発行の 1 回と再発行の 2 回で、リスナーは 3 回受ける
    void stopsResubmittingAtMaxAttempts() {
        var job = jobWithMaxAttempts(3);
        var event = new FailingEvent(UUID.randomUUID());
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
        await().atMost(TIMEOUT).until(() -> failedWith(event, 1));

        clock.advance(Duration.ofMinutes(6));
        job.resubmit();
        await().atMost(TIMEOUT).until(() -> failedWith(event, 2));
        clock.advance(Duration.ofMinutes(1));
        job.resubmit();
        await().atMost(TIMEOUT).until(() -> failedWith(event, 3));
        var lastResubmission = publicationOf(event).getLastResubmissionDate();

        clock.advance(Duration.ofMinutes(1));
        job.resubmit();

        // 再発行すると、再発行の日時と回数が変わる（リスナーは非同期で動くので、受けた回数だけでは確かめられない）
        var publication = publicationOf(event);
        assertThat(publication.getLastResubmissionDate()).isEqualTo(lastResubmission);
        assertThat(publication.getCompletionAttempts()).isEqualTo(3);
        assertThat(failingListener.attempts(event)).isEqualTo(3);
    }

    @Test
    @DisplayName("AC-290 処理の回数が上限に達する前に処理できたイベントは、処理を終える")
    void completesBeforeMaxAttempts() {
        var job = jobWithMaxAttempts(3);
        var event = failedOnce();

        clock.advance(Duration.ofMinutes(6));
        job.resubmit();

        await().atMost(TIMEOUT).until(() -> listener.attempts(event) == 2 && listener.succeeded(event));
    }

    /**
     * 処理の回数の上限を変えた再発行のジョブ（間隔と発行からの時間の下限は既定と同じ）
     */
    private EventResubmissionJob jobWithMaxAttempts(int maxAttempts) {
        return new EventResubmissionJob(incompletePublications,
                new EventResubmissionJob.Properties(Duration.ofMinutes(1), Duration.ofMinutes(5), maxAttempts), clock);
    }

    /**
     * テストのイベントの発行の記録が、指定の処理の回数で、処理に失敗した状態か。
     */
    private boolean failedWith(Object event, int completionAttempts) {
        var publication = publicationOf(event);
        return publication.getStatus() == EventPublication.Status.FAILED
                && publication.getCompletionAttempts() == completionAttempts;
    }

    /**
     * テストのイベントの、未完了の発行の記録
     */
    private TargetEventPublication publicationOf(Object event) {
        return registry.findIncompletePublications().stream()
                .filter(publication -> publication.getEvent().equals(event))
                .findFirst().orElseThrow();
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
     * 処理に失敗し続けるテストのイベント
     *
     * @param id イベントを見分ける ID
     */
    record FailingEvent(UUID id) {
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

        @Bean
        FailingListener failingListener() {
            return new FailingListener();
        }
    }

    /**
     * いつも失敗するリスナー。
     */
    static class FailingListener {

        /**
         * イベント ID → 受けた回数
         */
        private final Map<UUID, AtomicInteger> attempts = new ConcurrentHashMap<>();

        /**
         * 受けた回数を数え、例外で終わる。
         *
         * @param event テストのイベント
         */
        @ApplicationModuleListener
        void on(FailingEvent event) {
            attempts.computeIfAbsent(event.id(), id -> new AtomicInteger()).incrementAndGet();
            throw new IllegalStateException("いつも失敗させる");
        }

        int attempts(FailingEvent event) {
            var count = attempts.get(event.id());
            return count == null ? 0 : count.get();
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

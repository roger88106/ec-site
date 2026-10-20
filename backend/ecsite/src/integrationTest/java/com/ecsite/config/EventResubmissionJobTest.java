package com.ecsite.config;

import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.TestClockConfig;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
 * 未完了のイベントの再発行と、止めたイベントの統合テスト（T7104、T017-02-01、03、バックエンドのアーキテクチャ 第 5 節）。<br />
 * 1 回目だけ失敗するリスナーと、いつも失敗するリスナーで未完了のイベントを作り、時計を進めてから再発行のジョブを呼ぶ。
 * イベントはコミット後に非同期で処理されるので、処理を待つ。
 */
@IntegrationTest
@Import(TestClockConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class EventResubmissionJobTest {

    /**
     * テストの現在時刻（イベントの発行日時になる）
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * イベントの記録のテーブル（Spring Modulith の表。jOOQ の生成クラスはモジュールの外では使わない）
     */
    private static final Table<?> EVENT_PUBLICATION = DSL.table(DSL.name("common", "event_publication"));

    /**
     * イベントの記録の ID
     */
    private static final Field<UUID> ID = DSL.field(DSL.name("id"), UUID.class);

    /**
     * 処理の状態
     */
    private static final Field<String> STATUS = DSL.field(DSL.name("status"), String.class);

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

    @Autowired
    EventPublicationRecords publicationRecords;

    @Autowired
    EventCleanupJob cleanupJob;

    @Autowired
    DSLContext dsl;

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
        var event = failedUntil(job, 3);
        var lastResubmission = publicationOf(event).getLastResubmissionDate();

        clock.advance(Duration.ofMinutes(1));
        job.resubmit();

        // 再発行すると、再発行の日時と回数が変わる（リスナーは非同期で動くので、受けた回数だけでは確かめられない）
        var publication = publicationOf(event);
        assertThat(publication.getLastResubmissionDate()).isEqualTo(lastResubmission);
        assertThat(failingListener.attempts(event)).isEqualTo(3);
    }

    @Test
    @DisplayName("AC-291 最後の再発行が失敗したイベントは、次の回で止め、種類・イベント ID・リスナー・処理の回数をエラーのログに 1 回だけ出す。中身は出さない")
    void logsStoppedEventOnce(CapturedOutput output) {
        var job = jobWithMaxAttempts(2);
        var event = failedUntil(job, 2);
        var publication = publicationOf(event);

        clock.advance(Duration.ofMinutes(1));
        job.resubmit();
        clock.advance(Duration.ofMinutes(1));
        job.resubmit();

        var logLines = output.getAll().lines().filter(line -> line.contains(publication.getIdentifier().toString())).toList();
        assertThat(logLines).singleElement().satisfies(line -> assertThat(line)
                .contains("ERROR", FailingEvent.class.getName(), publication.getTargetIdentifier().getValue(), "処理の回数 2")
                .doesNotContain(event.email()));
        assertThat(output.getAll()).doesNotContain(event.email());
    }

    @Test
    @DisplayName("AC-293 止めたイベントは、処理の済んだイベントの片付けで消えない")
    void stoppedEventIsNotCleanedUp() {
        var job = jobWithMaxAttempts(2);
        var event = failedUntil(job, 2);
        clock.advance(Duration.ofMinutes(1));
        job.resubmit();
        assertThat(publicationOf(event).getCompletionAttempts()).isEqualTo(3);

        clock.advance(Duration.ofDays(30));
        cleanupJob.cleanUp();

        assertThat(publicationOf(event).getCompletionAttempts()).isEqualTo(3);
    }

    @Test
    @DisplayName("AC-291 最後の再発行が処理中（失敗も完了もしていない）のイベントは、止めない")
    void doesNotStopEventInProcess(CapturedOutput output) {
        var job = jobWithMaxAttempts(2);
        var event = failedUntil(job, 2);
        var publication = publicationOf(event);
        // 処理中に見せる（再発行の後、リスナーが終わる前の状態）
        dsl.update(EVENT_PUBLICATION).set(STATUS, "RESUBMITTED")
                .where(ID.eq(publication.getIdentifier())).execute();

        clock.advance(Duration.ofMinutes(1));
        job.resubmit();

        assertThat(publicationOf(event).getCompletionAttempts()).isEqualTo(2);
        assertThat(output.getAll()).doesNotContain(publication.getIdentifier().toString());
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
                new EventResubmissionJob.Properties(Duration.ofMinutes(1), Duration.ofMinutes(5), maxAttempts), clock,
                publicationRecords);
    }

    /**
     * いつも失敗するイベントを発行し、処理の回数が指定の回数になるまで再発行する（最後の処理も失敗する）。
     */
    private FailingEvent failedUntil(EventResubmissionJob job, int completionAttempts) {
        var event = new FailingEvent(UUID.randomUUID(), "stopped-" + UUID.randomUUID() + "@example.com");
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
        await().atMost(TIMEOUT).until(() -> failedWith(event, 1));
        clock.advance(Duration.ofMinutes(5));
        for (int attempts = 2; attempts <= completionAttempts; attempts++) {
            clock.advance(Duration.ofMinutes(1));
            job.resubmit();
            int expected = attempts;
            await().atMost(TIMEOUT).until(() -> failedWith(event, expected));
        }
        return event;
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
     * @param id    イベントを見分ける ID
     * @param email ログに出ないことを確かめる中身
     */
    record FailingEvent(UUID id, String email) {
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

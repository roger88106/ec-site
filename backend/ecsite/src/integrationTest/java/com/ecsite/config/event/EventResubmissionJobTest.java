package com.ecsite.config.event;

import com.ecsite.AsyncEvents;
import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.TestClockConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.jooq.DSLContext;
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
import static com.ecsite.config.event.EventPublications.COMPLETION_ATTEMPTS;
import static com.ecsite.config.event.EventPublications.COMPLETION_DATE;
import static com.ecsite.config.event.EventPublications.EVENT_PUBLICATION;
import static com.ecsite.config.event.EventPublications.ID;
import static com.ecsite.config.event.EventPublications.STATUS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 未完了のイベントの再発行と、止めたイベントの統合テスト（T7104、T017-02-01、03、05、バックエンドのアーキテクチャ 第 5 節）。<br />
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
    EventPublicationMetrics publicationMetrics;

    @Autowired
    MeterRegistry meterRegistry;

    @Autowired
    EventCleanupJob cleanupJob;

    @Autowired
    DSLContext dsl;

    @Autowired
    AsyncEvents asyncEvents;

    /**
     * イベントの発行を確かめる補助
     */
    EventPublications publications;

    @BeforeEach
    void setUp() {
        clock.setInstant(NOW);
        publications = new EventPublications(dsl, transactionTemplate, eventPublisher, registry, asyncEvents, clock);
    }

    @Test
    @DisplayName("処理に失敗したイベントは、5 分たつと再発行され、リスナーで処理される")
    void resubmitsFailedEventAfterMinAge() {
        var event = failedOnce();

        clock.advance(Duration.ofMinutes(6));
        job.resubmit();

        asyncEvents.awaitHandled(event::equals);
        assertThat(listener.attempts(event)).isEqualTo(2);
        assertThat(listener.succeeded(event)).isTrue();
    }

    @Test
    @DisplayName("処理に失敗してから 5 分たっていないイベントは、再発行しない")
    void doesNotResubmitRecentEvent() {
        var event = failedOnce();

        clock.advance(Duration.ofMinutes(4));
        job.resubmit();

        // 再発行すると、発行の記録の再発行の日時（初めは発行の日時）が変わる。リスナーは非同期で動くので、受けた回数だけでは確認できない
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

        // 再発行すると、再発行の日時と回数が変わる（リスナーは非同期で動くので、受けた回数だけでは確認できない）
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

        asyncEvents.awaitHandled(event::equals);
        assertThat(listener.attempts(event)).isEqualTo(2);
        assertThat(listener.succeeded(event)).isTrue();
    }

    @Test
    @DisplayName("AC-292 再発行のジョブの後、未完了のイベントの件数と止めたイベントの件数をメトリクスに出す")
    void publishesCountsAsMetrics() {
        var job = jobWithMaxAttempts(2);
        failedUntil(job, 2);
        clock.advance(Duration.ofMinutes(1));
        job.resubmit();
        // 何も再発行しないジョブ（発行からの時間の下限が長い）で、件数が動かない状態で数える
        var quietJob = jobWith(Duration.ofDays(365), 2);

        await().atMost(TIMEOUT).untilAsserted(() -> {
            quietJob.resubmit();
            assertThat(gauge("ecsite.events.stopped"))
                    .isEqualTo(dsl.fetchCount(EVENT_PUBLICATION, COMPLETION_DATE.isNull().and(COMPLETION_ATTEMPTS.gt(2))))
                    .isPositive();
            assertThat(gauge("ecsite.events.incomplete"))
                    .isEqualTo(dsl.fetchCount(EVENT_PUBLICATION, COMPLETION_DATE.isNull()));
        });
    }

    /**
     * メトリクスの値
     */
    private int gauge(String name) {
        return (int) meterRegistry.get(name).gauge().value();
    }

    /**
     * 処理の回数の上限を変えた再発行のジョブ（間隔と発行からの時間の下限は既定と同じ）
     */
    private EventResubmissionJob jobWithMaxAttempts(int maxAttempts) {
        return jobWith(Duration.ofMinutes(5), maxAttempts);
    }

    /**
     * 発行からの時間の下限と処理の回数の上限を変えた再発行のジョブ
     */
    private EventResubmissionJob jobWith(Duration minAge, int maxAttempts) {
        return new EventResubmissionJob(incompletePublications,
                new EventResubmissionJob.Properties(Duration.ofMinutes(1), minAge, maxAttempts), clock,
                publicationRecords, publicationMetrics);
    }

    /**
     * いつも失敗するイベントを発行し、処理の回数が指定の回数になるまで再発行する（最後の処理も失敗する）。
     */
    private FailingEvent failedUntil(EventResubmissionJob job, int completionAttempts) {
        var event = new FailingEvent(UUID.randomUUID(), "stopped-" + UUID.randomUUID() + "@example.com");
        return publications.failedUntil(event, job, completionAttempts);
    }

    /**
     * テストのイベントの、未完了の発行の記録
     */
    private TargetEventPublication publicationOf(Object event) {
        return publications.publicationOf(event);
    }

    /**
     * イベントをコミットするトランザクションで発行し、リスナーの 1 回目の失敗を待つ。
     */
    private FlakyEvent failedOnce() {
        return publications.failedOnce(new FlakyEvent(UUID.randomUUID()));
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
     * @param email ログに出ないことを確認する中身
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

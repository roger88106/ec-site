package com.ecsite.config.event;

import com.ecsite.AsyncEvents;
import com.ecsite.CapturedLogs;
import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import io.micrometer.core.instrument.MeterRegistry;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.config.StalenessProperties;
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
 * 未完了のイベントの再発行と、止めたイベントの統合テスト（バックエンドのアーキテクチャの「水平スケール」）。<br />
 * 1 回目だけ失敗するリスナーと、いつも失敗するリスナーで未完了のイベントを作り、時計を進めてから再発行のジョブを呼ぶ。
 * イベントはコミット後に非同期で処理されるので、処理を待つ。
 */
@IntegrationTest
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

    @Autowired
    StalenessProperties staleness;

    /**
     * イベントの発行を確認する補助
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
        var logStart = CapturedLogs.mark(output);
        job.resubmit();
        clock.advance(Duration.ofMinutes(1));
        job.resubmit();

        var logLines = CapturedLogs.since(output, logStart).lines().filter(line -> line.contains(publication.getIdentifier().toString())).toList();
        assertThat(logLines).singleElement().satisfies(line -> assertThat(line)
                .contains("ERROR", FailingEvent.class.getName(), publication.getTargetIdentifier().getValue(), "処理の回数 2")
                .doesNotContain(event.email()));
        assertThat(CapturedLogs.since(output, logStart)).doesNotContain(event.email());
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
        markStatus(event, "RESUBMITTED");

        clock.advance(Duration.ofMinutes(1));
        var logStart = CapturedLogs.mark(output);
        job.resubmit();

        assertThat(publicationOf(event).getCompletionAttempts()).isEqualTo(2);
        assertThat(CapturedLogs.since(output, logStart)).doesNotContain(publication.getIdentifier().toString());
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

    @Test
    @Timeout(60)
    @DisplayName("3 台で動かしても、処理の回数が上限に達するまでの時間は 1 台と同じ")
    void timeToMaxAttemptsDoesNotDependOnInstanceCount() {
        var oneInstance = timeToReachMaxAttempts(Duration.ofMinutes(1));
        // 3 台のタイマーが 20 秒ずつずれていて、合わせて 1 分に 3 回ジョブを呼ぶ
        var threeInstances = timeToReachMaxAttempts(Duration.ofSeconds(20));

        assertThat(oneInstance).isEqualTo(Duration.ofMinutes(2));
        assertThat(threeInstances).isEqualTo(oneInstance);
    }

    @Test
    @DisplayName("再発行の間隔を設定で変えると、同じイベントの試行の間も、その間隔を空ける")
    void waitsForConfiguredIntervalBetweenAttempts() {
        var event = failedUntil(jobWithMaxAttempts(3), 2);
        var job = jobWith(Duration.ofMinutes(3), Duration.ofMinutes(5), 3);

        clock.advance(Duration.ofMinutes(2));
        job.resubmit();
        assertThat(publicationOf(event).getCompletionAttempts()).isEqualTo(2);

        clock.advance(Duration.ofMinutes(1));
        job.resubmit();
        asyncEvents.awaitFailed(event::equals, 3);
        assertThat(failingListener.attempts(event)).isEqualTo(3);
    }

    @Test
    @DisplayName("再発行の印を付けた後でインスタンスが止まったイベントは、最後の試行から 5 分たつと再発行される")
    void resubmitsEventStuckAfterResubmissionMark() {
        var job = jobWithMaxAttempts(3);
        var event = failedUntil(job, 2);
        markStatus(event, "RESUBMITTED");

        clock.advance(Duration.ofMinutes(6));
        registry.markStalePublicationsFailed(staleness);
        job.resubmit();

        asyncEvents.awaitFailed(event::equals, 3);
        assertThat(failingListener.attempts(event)).isEqualTo(3);
    }

    @Test
    @DisplayName("最後の試行から 5 分たっていない処理中のイベントは、失敗の扱いにしない")
    void doesNotFailRecentEventInProcess() {
        var job = jobWithMaxAttempts(3);
        var event = failedUntil(job, 2);
        markStatus(event, "RESUBMITTED");

        clock.advance(Duration.ofMinutes(4));
        registry.markStalePublicationsFailed(staleness);

        assertThat(dsl.select(STATUS).from(EVENT_PUBLICATION).where(ID.eq(publicationOf(event).getIdentifier())).fetchOne(STATUS))
                .isEqualTo("RESUBMITTED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUBLISHED", "PROCESSING", "RESUBMITTED"})
    @DisplayName("AC-291 処理の回数が上限に達したイベントが、結果の出ないまま最後の試行から 5 分たつと、止めてエラーのログに出力する")
    void stopsEventStuckAtMaxAttempts(String status, CapturedOutput output) {
        var job = jobWithMaxAttempts(2);
        var event = failedUntil(job, 2);
        var publication = publicationOf(event);
        markStatus(event, status);

        clock.advance(Duration.ofMinutes(6));
        var logStart = CapturedLogs.mark(output);
        registry.markStalePublicationsFailed(staleness);
        job.resubmit();

        assertThat(publicationOf(event).getCompletionAttempts()).isEqualTo(3);
        assertThat(CapturedLogs.since(output, logStart).lines()
                .filter(line -> line.contains("ERROR") && line.contains(publication.getIdentifier().toString())))
                .hasSize(1);
    }

    /**
     * 1 回目の処理に失敗したイベントについて、ジョブを決まった間隔で呼び、処理の回数が上限（4）に達するまでの時間を測る。
     *
     * @param timerGap ジョブを呼ぶ間隔
     * @return 再発行の対象になってから、処理の回数が上限に達するまでの時間
     */
    private Duration timeToReachMaxAttempts(Duration timerGap) {
        var maxAttempts = 4;
        var job = jobWithMaxAttempts(maxAttempts);
        var event = publications.failedOnce(new FailingEvent(UUID.randomUUID(), "budget-" + UUID.randomUUID() + "@example.com"));
        clock.advance(Duration.ofMinutes(6));
        var start = clock.instant();
        var attempts = 1;
        while (true) {
            job.resubmit();
            var current = publicationOf(event).getCompletionAttempts();
            if (current > attempts) {
                attempts = current;
                asyncEvents.awaitFailed(event::equals, attempts);
            }
            if (attempts >= maxAttempts) {
                return Duration.between(start, clock.instant());
            }
            clock.advance(timerGap);
        }
    }

    /**
     * イベントの記録の状態を書き換え、インスタンスが処理の途中で止まった状態に見せる。
     *
     * @param event  発行したイベント
     * @param status 処理の状態
     */
    private void markStatus(Object event, String status) {
        dsl.update(EVENT_PUBLICATION).set(STATUS, status)
                .where(ID.eq(publicationOf(event).getIdentifier())).execute();
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
        return jobWith(Duration.ofMinutes(1), minAge, maxAttempts);
    }

    /**
     * 間隔、発行からの時間の下限、処理の回数の上限を変えた再発行のジョブ
     */
    private EventResubmissionJob jobWith(Duration interval, Duration minAge, int maxAttempts) {
        return new EventResubmissionJob(incompletePublications,
                new EventResubmissionJob.Properties(interval, minAge, maxAttempts), clock,
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

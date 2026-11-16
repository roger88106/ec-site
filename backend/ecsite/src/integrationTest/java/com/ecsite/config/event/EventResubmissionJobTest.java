package com.ecsite.config.event;

import com.ecsite.AsyncEvents;
import com.ecsite.IntegrationTest;
import com.ecsite.LogCapture;
import com.ecsite.MutableClock;
import io.micrometer.core.instrument.MeterRegistry;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.config.StalenessProperties;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.modulith.events.core.TargetEventPublication;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
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
 * 未完了のイベントの再発行と、止めたイベントの統合テスト
 *
 * <p>バックエンドのアーキテクチャの「水平スケール」に対応する。
 * 1 回目だけ失敗するリスナーと、いつも失敗するリスナーで未完了のイベントを作り、時計を進めてから再発行のジョブを呼ぶ。
 * イベントはコミット後に非同期で処理されるので、処理を待つ。
 *
 * <p>テストは入れ子のクラスに置くので、テストのリスナーの設定は {@code @Import} で読み込む（入れ子のクラスは、外のクラスの中の設定を自動では探さない）。
 */
@IntegrationTest
@Import(EventResubmissionJobTest.Config.class)
class EventResubmissionJobTest {

    // ============================== Bean ==============================

    /**
     * テストするジョブ（設定の既定の間隔・発行からの時間の下限・処理の回数の上限）
     */
    @Autowired
    EventResubmissionJob job;

    /**
     * 1 回目だけ失敗するリスナー
     */
    @Autowired
    FlakyListener listener;

    /**
     * いつも失敗するリスナー
     */
    @Autowired
    FailingListener failingListener;

    /**
     * 未完了のイベントの再発行（設定を変えたジョブを作るのに使う）
     */
    @Autowired
    IncompleteEventPublications incompletePublications;

    /**
     * テストの時計
     */
    @Autowired
    MutableClock clock;

    /**
     * イベントの発行
     */
    @Autowired
    ApplicationEventPublisher eventPublisher;

    /**
     * 発行をコミットするトランザクション
     */
    @Autowired
    TransactionTemplate transactionTemplate;

    /**
     * イベントの発行の記録
     */
    @Autowired
    EventPublicationRegistry registry;

    /**
     * 止めたイベントの記録（設定を変えたジョブを作るのに使う）
     */
    @Autowired
    EventPublicationRecords publicationRecords;

    /**
     * 件数のメトリクス（設定を変えたジョブを作るのに使う）
     */
    @Autowired
    EventPublicationMetrics publicationMetrics;

    /**
     * メトリクスの登録先
     */
    @Autowired
    MeterRegistry meterRegistry;

    /**
     * 処理の済んだイベントの片付けのジョブ
     */
    @Autowired
    EventCleanupJob cleanupJob;

    /**
     * DB を読み書きする jOOQ
     */
    @Autowired
    DSLContext dsl;

    /**
     * 非同期のリスナーを待つ補助
     */
    @Autowired
    AsyncEvents asyncEvents;

    /**
     * 結果の出ないイベントを失敗の扱いにするまでの時間の設定
     */
    @Autowired
    StalenessProperties staleness;

    /**
     * イベントの発行を確認する補助
     */
    EventPublications publications;

    @BeforeEach
    void setUp() {
        publications = new EventPublications(dsl, transactionTemplate, eventPublisher, registry, asyncEvents, clock);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("再発行")
    class Resubmission {

        @Test
        @DisplayName("処理に失敗したイベントは、5 分たつと再発行され、リスナーで処理される")
        void resubmitsFailedEventAfterMinAge() {
            // 準備：1 回目の処理に失敗したイベント
            var event = failedOnce();

            // 操作：6 分後に再発行する
            clock.advance(Duration.ofMinutes(6));
            job.resubmit();

            // 確認：2 回目の処理で成功する
            asyncEvents.awaitHandled(event::equals);
            assertThat(listener.attempts(event)).isEqualTo(2);
            assertThat(listener.succeeded(event)).isTrue();
        }

        @Test
        @DisplayName("処理に失敗してから 5 分たっていないイベントは、再発行しない")
        void doesNotResubmitRecentEvent() {
            // 準備：1 回目の処理に失敗したイベント
            var event = failedOnce();

            // 操作：4 分後に再発行する
            clock.advance(Duration.ofMinutes(4));
            job.resubmit();

            // 確認：再発行の日時が発行の日時のまま（リスナーは非同期で動くので、受けた回数だけでは確認できない）で、リスナーは 1 回だけ受けた
            var publication = publicationOf(event);
            assertThat(publication.getLastResubmissionDate()).isEqualTo(publication.getPublicationDate());
            assertThat(listener.attempts(event)).isEqualTo(1);
        }

        @Test
        @DisplayName("処理に失敗してからちょうど 5 分のイベントは、まだ再発行しない")
        void doesNotResubmitAtExactlyMinAge() {
            // 準備：1 回目の処理に失敗したイベント
            var event = failedOnce();

            // 操作：ちょうど 5 分後に再発行する
            clock.advance(Duration.ofMinutes(5));
            job.resubmit();

            // 確認：再発行の日時が発行の日時のままで、リスナーは 1 回だけ受けた
            var publication = publicationOf(event);
            assertThat(publication.getLastResubmissionDate()).isEqualTo(publication.getPublicationDate());
            assertThat(listener.attempts(event)).isEqualTo(1);
        }

        @Test
        @DisplayName("処理に失敗してから 5 分を 1 ミリ秒でも過ぎたイベントは、再発行され、リスナーで処理される")
        void resubmitsJustAfterMinAge() {
            // 準備：1 回目の処理に失敗したイベント
            var event = failedOnce();

            // 操作：5 分と 1 ミリ秒後に再発行する
            clock.advance(Duration.ofMinutes(5).plusMillis(1));
            job.resubmit();

            // 確認：2 回目の処理で成功する
            asyncEvents.awaitHandled(event::equals);
            assertThat(listener.attempts(event)).isEqualTo(2);
            assertThat(listener.succeeded(event)).isTrue();
        }

        @Test
        @DisplayName("AC-290 処理の回数が上限に達する前に処理できたイベントは、処理を終える")
        void completesBeforeMaxAttempts() {
            // 準備：処理の回数の上限 3 のジョブと、1 回目の処理に失敗したイベント
            var job = jobWithMaxAttempts(3);
            var event = failedOnce();

            // 操作：6 分後に再発行する
            clock.advance(Duration.ofMinutes(6));
            job.resubmit();

            // 確認：2 回目の処理で成功する
            asyncEvents.awaitHandled(event::equals);
            assertThat(listener.attempts(event)).isEqualTo(2);
            assertThat(listener.succeeded(event)).isTrue();
        }

        @Test
        @DisplayName("再発行の間隔を設定で変えると、同じイベントの試行の間も、その間隔を空ける")
        void waitsForConfiguredIntervalBetweenAttempts() {
            // 準備：処理を 2 回失敗したイベントと、間隔 3 分のジョブ
            var event = failedUntil(jobWithMaxAttempts(3), 2);
            var job = jobWith(Duration.ofMinutes(3), Duration.ofMinutes(5), 3);

            // 操作と確認：最後の試行から 2 分では、再発行しない
            clock.advance(Duration.ofMinutes(2));
            job.resubmit();
            assertThat(publicationOf(event).getCompletionAttempts()).isEqualTo(2);

            // 操作と確認：最後の試行から 3 分で、再発行する
            clock.advance(Duration.ofMinutes(1));
            job.resubmit();
            asyncEvents.awaitFailed(event::equals, 3);
            assertThat(failingListener.attempts(event)).isEqualTo(3);
        }

        @Test
        @Timeout(60)
        @DisplayName("3 台で動かしても、処理の回数が上限に達するまでの時間は 1 台と同じ")
        void timeToMaxAttemptsDoesNotDependOnInstanceCount() {
            // 操作：1 台（1 分ごと）と、3 台（タイマーが 20 秒ずつずれて、合わせて 1 分に 3 回）で、上限に達するまでの時間を測る
            var oneInstance = timeToReachMaxAttempts(Duration.ofMinutes(1));
            var threeInstances = timeToReachMaxAttempts(Duration.ofSeconds(20));

            // 確認：どちらも 2 分
            assertThat(oneInstance).isEqualTo(Duration.ofMinutes(2));
            assertThat(threeInstances).isEqualTo(oneInstance);
        }
    }

    @Nested
    @DisplayName("処理の回数の上限で止める")
    class Stop {

        /**
         * 全てのロガーのログ（ログにイベントの中身が出ないことを、どのロガーについても確認するため）
         */
        @RegisterExtension
        final LogCapture logs = LogCapture.all();

        @Test
        @DisplayName("AC-290 処理に失敗し続けるイベントは、処理の回数が上限に達したら、それ以上再発行しない")
        void stopsResubmittingAtMaxAttempts() {
            // 準備：処理の回数の上限 3 のジョブで、処理を 3 回失敗したイベント。
            // 処理の回数（completion_attempts）は、発行の時点で 1、再発行するたびに 1 つ増える（Spring Modulith 2.1 の動き）。
            // 上限 3 なら、発行の 1 回と再発行の 2 回で、リスナーは 3 回受ける
            var job = jobWithMaxAttempts(3);
            var event = failedUntil(job, 3);
            var lastResubmission = publicationOf(event).getLastResubmissionDate();

            // 操作：1 分後に再発行する
            clock.advance(Duration.ofMinutes(1));
            job.resubmit();

            // 確認：再発行の日時が変わらず（リスナーは非同期で動くので、受けた回数だけでは確認できない）、リスナーは 3 回だけ受けた
            assertThat(publicationOf(event).getLastResubmissionDate()).isEqualTo(lastResubmission);
            assertThat(failingListener.attempts(event)).isEqualTo(3);
        }

        @Test
        @DisplayName("AC-291 最後の再発行が失敗したイベントを止めると、エラーのログに 1 回だけ出す")
        void logsStoppedEventOnce() {
            // 準備：処理の回数の上限 2 のジョブで、処理を 2 回失敗したイベント
            var job = jobWithMaxAttempts(2);
            var event = failedUntil(job, 2);
            var publication = publicationOf(event);

            // 操作：次の回で止め、その次の回も動かす
            logs.clear();
            resubmitTwice(job);

            // 確認：この発行の記録のログが、エラーで 1 件だけ
            assertThat(logsOf(publication)).singleElement()
                    .satisfies(entry -> assertThat(entry.level()).isEqualTo(Level.ERROR));
        }

        @Test
        @DisplayName("AC-291 止めたイベントのログに、種類・イベント ID・リスナー・処理の回数を出す")
        void logsStoppedEventDetails() {
            // 準備：処理の回数の上限 2 のジョブで、処理を 2 回失敗したイベント
            var job = jobWithMaxAttempts(2);
            var event = failedUntil(job, 2);
            var publication = publicationOf(event);

            // 操作：次の回で止める
            logs.clear();
            resubmitTwice(job);

            // 確認：この発行の記録（イベント ID）のログに、イベントの種類・リスナー・処理の回数がある
            assertThat(logsOf(publication)).singleElement().satisfies(entry -> assertThat(entry.text())
                    .contains(FailingEvent.class.getName(), publication.getTargetIdentifier().getValue(), "処理の回数 2"));
        }

        @Test
        @DisplayName("AC-291 止めたイベントの中身は、どのログにも出さない")
        void doesNotLogStoppedEventContent() {
            // 準備：処理の回数の上限 2 のジョブで、処理を 2 回失敗したイベント
            var job = jobWithMaxAttempts(2);
            var event = failedUntil(job, 2);

            // 操作：次の回で止める
            logs.clear();
            resubmitTwice(job);

            // 確認：イベントの中身（メールアドレス）が、止めたログにも、ほかのログにもない
            assertThat(logsOf(publicationOf(event))).singleElement()
                    .satisfies(entry -> assertThat(entry.text()).doesNotContain(event.email()));
            assertThat(logs.text()).doesNotContain(event.email());
        }

        @Test
        @DisplayName("AC-291 最後の再発行が処理中（失敗も完了もしていない）のイベントは、止めない")
        void doesNotStopEventInProcess() {
            // 準備：処理を 2 回失敗したイベントを、処理中（再発行の後、リスナーが終わる前の状態）に見せる
            var job = jobWithMaxAttempts(2);
            var event = failedUntil(job, 2);
            var publication = publicationOf(event);
            markStatus(event, "RESUBMITTED");

            // 操作：1 分後に再発行する
            clock.advance(Duration.ofMinutes(1));
            logs.clear();
            job.resubmit();

            // 確認：処理の回数が変わらず、この発行の記録のログがない
            assertThat(publicationOf(event).getCompletionAttempts()).isEqualTo(2);
            assertThat(logs.text()).doesNotContain(publication.getIdentifier().toString());
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"PUBLISHED", "PROCESSING", "RESUBMITTED"})
        @DisplayName("AC-291 処理の回数が上限に達したイベントが、結果の出ないまま最後の試行から 5 分たつと、止めてエラーのログに出力する")
        void stopsEventStuckAtMaxAttempts(String status) {
            // 準備：処理を 2 回失敗したイベントを、結果の出ない状態に見せる
            var job = jobWithMaxAttempts(2);
            var event = failedUntil(job, 2);
            var publication = publicationOf(event);
            markStatus(event, status);

            // 操作：6 分後に、結果の出ないイベントを失敗の扱いにしてから、再発行する
            clock.advance(Duration.ofMinutes(6));
            logs.clear();
            registry.markStalePublicationsFailed(staleness);
            job.resubmit();

            // 確認：止めた印（処理の回数が上限を超える）が付き、この発行の記録のエラーのログが 1 件
            assertThat(publicationOf(event).getCompletionAttempts()).isEqualTo(3);
            assertThat(logsOf(publication)).filteredOn(entry -> entry.level() == Level.ERROR).hasSize(1);
        }

        @Test
        @DisplayName("AC-293 止めたイベントは、処理の済んだイベントの片付けで消えない")
        void stoppedEventIsNotCleanedUp() {
            // 準備：処理の回数の上限 2 のジョブで止めたイベント
            var job = jobWithMaxAttempts(2);
            var event = failedUntil(job, 2);
            clock.advance(Duration.ofMinutes(1));
            job.resubmit();

            // 確認（前提）：止めた印が付いている
            assertThat(publicationOf(event).getCompletionAttempts()).isEqualTo(3);

            // 操作：30 日後に片付ける
            clock.advance(Duration.ofDays(30));
            cleanupJob.cleanUp();

            // 確認：止めたイベントの記録が残る
            assertThat(publicationOf(event).getCompletionAttempts()).isEqualTo(3);
        }

        /**
         * 1 分ずつ時計を進めて、ジョブを 2 回呼ぶ（1 回目で止め、2 回目で再びログに出さないことを見る）。
         *
         * @param job 再発行のジョブ
         */
        private void resubmitTwice(EventResubmissionJob job) {
            clock.advance(Duration.ofMinutes(1));
            job.resubmit();
            clock.advance(Duration.ofMinutes(1));
            job.resubmit();
        }

        /**
         * 発行の記録の ID を含むログを返す。
         *
         * @param publication 発行の記録
         * @return ログ
         */
        private List<LogCapture.LogEntry> logsOf(TargetEventPublication publication) {
            return logs.entries().stream().filter(entry -> entry.text().contains(publication.getIdentifier().toString())).toList();
        }
    }

    @Nested
    @DisplayName("処理の途中で止まったインスタンス")
    class StuckInstance {

        @Test
        @DisplayName("再発行の印を付けた後でインスタンスが止まったイベントは、最後の試行から 5 分たつと再発行される")
        void resubmitsEventStuckAfterResubmissionMark() {
            // 準備：処理を 2 回失敗したイベントを、再発行の印を付けたまま止まった状態に見せる
            var job = jobWithMaxAttempts(3);
            var event = failedUntil(job, 2);
            markStatus(event, "RESUBMITTED");

            // 操作：6 分後に、結果の出ないイベントを失敗の扱いにしてから、再発行する
            clock.advance(Duration.ofMinutes(6));
            registry.markStalePublicationsFailed(staleness);
            job.resubmit();

            // 確認：3 回目の処理が動く
            asyncEvents.awaitFailed(event::equals, 3);
            assertThat(failingListener.attempts(event)).isEqualTo(3);
        }

        @Test
        @DisplayName("最後の試行から 5 分たっていない処理中のイベントは、失敗の扱いにしない")
        void doesNotFailRecentEventInProcess() {
            // 準備：処理を 2 回失敗したイベントを、処理中に見せる
            var job = jobWithMaxAttempts(3);
            var event = failedUntil(job, 2);
            markStatus(event, "RESUBMITTED");

            // 操作：4 分後に、結果の出ないイベントを失敗の扱いにする
            clock.advance(Duration.ofMinutes(4));
            registry.markStalePublicationsFailed(staleness);

            // 確認：処理中のまま
            assertThat(dsl.select(STATUS).from(EVENT_PUBLICATION).where(ID.eq(publicationOf(event).getIdentifier())).fetchOne(STATUS))
                    .isEqualTo("RESUBMITTED");
        }
    }

    @Nested
    @DisplayName("件数のメトリクス")
    class Metrics {

        @Test
        @DisplayName("AC-292 再発行のジョブの後、未完了のイベントの件数と止めたイベントの件数をメトリクスに出す")
        void publishesCountsAsMetrics() {
            // 準備：止めたイベントを 1 件作る
            var job = jobWithMaxAttempts(2);
            failedUntil(job, 2);
            clock.advance(Duration.ofMinutes(1));
            job.resubmit();
            // 何も再発行しないジョブ（発行からの時間の下限が長い）で、件数が動かない状態で数える
            var quietJob = jobWith(Duration.ofDays(365), 2);

            // 操作と確認：ジョブの後のメトリクスが、DB の未完了・止めた件数と同じ（ほかのテストの非同期の処理で件数が動くので、合うまで繰り返す）
            await().atMost(TIMEOUT).untilAsserted(() -> {
                quietJob.resubmit();
                assertThat(gauge("ecsite.events.stopped"))
                        .isEqualTo(dsl.fetchCount(EVENT_PUBLICATION, COMPLETION_DATE.isNull().and(COMPLETION_ATTEMPTS.gt(2))))
                        .isPositive();
                assertThat(gauge("ecsite.events.incomplete"))
                        .isEqualTo(dsl.fetchCount(EVENT_PUBLICATION, COMPLETION_DATE.isNull()));
            });
        }
    }

    // ============================== 準備の補助 ==============================

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
     * メトリクスの値を返す。
     *
     * @param name メトリクスの名前
     * @return 値
     */
    private int gauge(String name) {
        return (int) meterRegistry.get(name).gauge().value();
    }

    /**
     * 処理の回数の上限を変えた再発行のジョブを作る（間隔と発行からの時間の下限は既定と同じ）。
     *
     * @param maxAttempts 処理の回数の上限
     * @return 再発行のジョブ
     */
    private EventResubmissionJob jobWithMaxAttempts(int maxAttempts) {
        return jobWith(Duration.ofMinutes(5), maxAttempts);
    }

    /**
     * 発行からの時間の下限と処理の回数の上限を変えた再発行のジョブを作る（間隔は既定と同じ）。
     *
     * @param minAge      発行からの時間の下限
     * @param maxAttempts 処理の回数の上限
     * @return 再発行のジョブ
     */
    private EventResubmissionJob jobWith(Duration minAge, int maxAttempts) {
        return jobWith(Duration.ofMinutes(1), minAge, maxAttempts);
    }

    /**
     * 間隔、発行からの時間の下限、処理の回数の上限を変えた再発行のジョブを作る。
     *
     * @param interval    間隔
     * @param minAge      発行からの時間の下限
     * @param maxAttempts 処理の回数の上限
     * @return 再発行のジョブ
     */
    private EventResubmissionJob jobWith(Duration interval, Duration minAge, int maxAttempts) {
        return new EventResubmissionJob(incompletePublications,
                new EventResubmissionJob.Properties(interval, minAge, maxAttempts), clock,
                publicationRecords, publicationMetrics);
    }

    /**
     * いつも失敗するイベントを発行し、処理の回数が指定の回数になるまで再発行する（最後の処理も失敗する）。
     *
     * @param job                再発行のジョブ
     * @param completionAttempts 処理の回数
     * @return 発行したイベント
     */
    private FailingEvent failedUntil(EventResubmissionJob job, int completionAttempts) {
        var event = new FailingEvent(UUID.randomUUID(), "stopped-" + UUID.randomUUID() + "@example.com");
        return publications.failedUntil(event, job, completionAttempts);
    }

    /**
     * テストのイベントの、未完了の発行の記録を返す。
     *
     * @param event 発行したイベント
     * @return 未完了の発行の記録
     */
    private TargetEventPublication publicationOf(Object event) {
        return publications.publicationOf(event);
    }

    /**
     * イベントをコミットするトランザクションで発行し、リスナーの 1 回目の失敗を待つ。
     *
     * @return 発行したイベント
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
     * テストのリスナーを登録する設定
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
     * いつも失敗するリスナー
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

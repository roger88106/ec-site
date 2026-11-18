package com.ecsite.config.event;

import com.ecsite.support.LogCapture;
import com.ecsite.support.MutableClock;
import com.ecsite.support.async.AsyncEvents;
import com.ecsite.support.config.IntegrationTest;
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
import org.springframework.modulith.events.EventPublication;
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

import static com.ecsite.config.event.EventPublications.COMPLETION_ATTEMPTS;
import static com.ecsite.config.event.EventPublications.EVENT_PUBLICATION;
import static com.ecsite.config.event.EventPublications.ID;
import static com.ecsite.config.event.EventPublications.STATUS;
import static org.assertj.core.api.Assertions.assertThat;

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

    // ============================== 定数 ==============================

    /**
     * 止めたイベントを、イベント発行記録の表に戻す SQL（設計書の復旧の手順と同じ SQL。ID を bind 変数で渡す）
     *
     * <p>1 つの文で、止めたイベントの表から消して、イベント発行記録の表に入れる。
     * 状態を失敗、処理の回数を 1、完了日時を空にして戻す。
     */
    private static final String RESTORE_STOPPED_EVENT = """
            WITH restored AS (
                DELETE FROM common.event_publication_stopped
                WHERE id = ?
                RETURNING id, listener_id, event_type, serialized_event, publication_date, last_resubmission_date
            )
            INSERT INTO common.event_publication
                (id, listener_id, event_type, serialized_event, publication_date,
                 completion_date, status, completion_attempts, last_resubmission_date)
            SELECT id, listener_id, event_type, serialized_event, publication_date,
                   NULL, 'FAILED', 1, last_resubmission_date
            FROM restored
            """;

    /**
     * 止めたイベントを、止めたイベントの表から消す SQL（設計書の復旧の手順と同じ SQL。ID を bind 変数で渡す）
     *
     * <p>不要なイベントを消すときに使う。
     */
    private static final String DELETE_STOPPED_EVENT = """
            DELETE FROM common.event_publication_stopped
            WHERE id = ?
            """;

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

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"PUBLISHED", "PROCESSING", "RESUBMITTED"})
        @DisplayName("結果の出ていないイベントは、発行から 5 分を過ぎても再発行しない")
        void doesNotResubmitEventWithoutResult(String status) {
            // 準備：処理を 2 回失敗したイベントを、結果の出ていない状態にする
            var job = jobWithMaxAttempts(3);
            var event = failedUntil(job, 2);
            var lastResubmission = publicationOf(event).getLastResubmissionDate();
            markStatus(event, status);

            // 操作：6 分後に再発行する（結果の出ないイベントを失敗の扱いにする監視は動かさない）
            clock.advance(Duration.ofMinutes(6));
            job.resubmit();

            // 確認：処理の回数と最後の再発行の日時が変わらない。リスナーが受けた回数も 2 回のまま（非同期で動くので、回数だけでは確認にならない）
            var publication = publicationOf(event);
            assertThat(publication.getCompletionAttempts()).isEqualTo(2);
            assertThat(publication.getLastResubmissionDate()).isEqualTo(lastResubmission);
            assertThat(failingListener.attempts(event)).isEqualTo(2);
        }

        @Test
        @DisplayName("最後の処理に失敗したイベントを再発行するとき、処理中のイベントは再発行しない")
        void resubmitsOnlyFailedEvents() {
            // 準備：処理中に見せたイベントと、1 回目の処理に失敗したイベント
            var inProcess = publications.failedOnce(failingEvent("in-process"));
            markStatus(inProcess, "PROCESSING");
            var lastResubmission = publicationOf(inProcess).getLastResubmissionDate();
            var failed = publications.failedOnce(failingEvent("failed"));

            // 操作：6 分後に再発行する（結果の出ないイベントを失敗の扱いにする監視は動かさない）
            clock.advance(Duration.ofMinutes(6));
            job.resubmit();

            // 確認：失敗したイベントは 2 回目の処理が動き、処理中のイベントは状態・処理の回数・最後の再発行の日時が変わらない
            asyncEvents.awaitFailed(failed::equals, 2);
            assertThat(failingListener.attempts(failed)).isEqualTo(2);
            var publication = publicationOf(inProcess);
            assertThat(statusOf(publication)).isEqualTo("PROCESSING");
            assertThat(publication.getCompletionAttempts()).isEqualTo(1);
            assertThat(publication.getLastResubmissionDate()).isEqualTo(lastResubmission);
            assertThat(failingListener.attempts(inProcess)).isEqualTo(1);
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
        @DisplayName("処理を終えたイベントは、時計を進めて再発行のジョブを動かしても、再発行しない")
        void doesNotResubmitCompletedEvent() {
            // 準備：1 回目の処理に失敗し、再発行の 2 回目の処理で終えたイベント
            var event = failedOnce();
            var publicationId = publicationOf(event).getIdentifier();
            clock.advance(Duration.ofMinutes(6));
            job.resubmit();
            asyncEvents.awaitHandled(event::equals);

            // 操作：さらに 6 分後に再発行する
            clock.advance(Duration.ofMinutes(6));
            job.resubmit();

            // 確認：処理の回数が 2 のまま。リスナーが受けた回数も 2 回のまま（非同期で動くので、回数だけでは確認にならない）
            assertThat(dsl.select(COMPLETION_ATTEMPTS).from(EVENT_PUBLICATION).where(ID.eq(publicationId)).fetchOne(COMPLETION_ATTEMPTS))
                    .isEqualTo(2);
            assertThat(listener.attempts(event)).isEqualTo(2);
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

            // 確認：止めたイベントの表に、再発行の日時が変わらないまま移り（リスナーは非同期で動くので、受けた回数だけでは確認できない）、
            // リスナーは 3 回だけ受けた
            assertThat(publications.stoppedOf(event.id())).hasValueSatisfying(stopped ->
                    assertThat(stopped.lastResubmissionDate()).isEqualTo(lastResubmission));
            assertThat(failingListener.attempts(event)).isEqualTo(3);
        }

        @Test
        @DisplayName("AC-290 最後の再発行が失敗したイベントは、次の回で止めたイベントの表に移り、イベント発行記録の表から消える")
        void movesStoppedEventToStoppedTable() {
            // 準備：処理の回数の上限 2 のジョブで、処理を 2 回失敗したイベント
            var job = jobWithMaxAttempts(2);
            var event = failedUntil(job, 2);
            var publication = publicationOf(event);

            // 操作：1 分後に再発行する
            clock.advance(Duration.ofMinutes(1));
            job.resubmit();

            // 確認：止めたイベントの表に、記録の ID・リスナー・型・処理の回数・最後の再発行の日時を保ったまま移り、記録の表にない
            assertThat(publications.stoppedOf(event.id())).hasValueSatisfying(stopped -> {
                assertThat(stopped.id()).isEqualTo(publication.getIdentifier());
                assertThat(stopped.listenerId()).isEqualTo(publication.getTargetIdentifier().getValue());
                assertThat(stopped.eventType()).isEqualTo(FailingEvent.class.getName());
                assertThat(stopped.completionAttempts()).isEqualTo(2);
                assertThat(stopped.lastResubmissionDate()).isEqualTo(publication.getLastResubmissionDate());
                assertThat(stopped.stoppedDate()).isNotNull();
            });
            assertThat(publications.count(event.id())).isZero();
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

            // 確認：止めたイベントの表に移り、この発行の記録のログが、エラーで 1 件だけ
            assertThat(publications.stoppedOf(event.id())).isPresent();
            assertThat(logsOf(publication)).singleElement()
                    .satisfies(entry -> assertThat(entry.level()).isEqualTo(Level.ERROR));
        }

        @Test
        @DisplayName("AC-290、291 処理の回数の上限を今の処理の回数より小さく下げると、上限以上の失敗したイベントは全て移り、エラーのログに 1 回ずつ出す")
        void movesAllFailedEventsAtOrAboveLoweredMaxAttempts() {
            // 準備：処理の回数の上限 3 のジョブで、処理を 3 回失敗したイベントと、2 回失敗したイベント
            var job = jobWithMaxAttempts(3);
            var third = publications.failedOnce(failingEvent("third"));
            clock.advance(Duration.ofMinutes(6));
            job.resubmit();
            asyncEvents.awaitFailed(third::equals, 2);
            var second = publications.failedOnce(failingEvent("second"));
            clock.advance(Duration.ofMinutes(6));
            job.resubmit();
            asyncEvents.awaitFailed(third::equals, 3);
            asyncEvents.awaitFailed(second::equals, 2);
            var thirdPublication = publicationOf(third);
            var secondPublication = publicationOf(second);

            // 操作：上限を 2 に下げたジョブを、1 分ずつ 2 回動かす
            logs.clear();
            resubmitTwice(jobWithMaxAttempts(2));

            // 確認：上限を超えたイベントも、上限ちょうどのイベントも、止めたイベントの表に移り、それぞれのエラーのログが 1 件だけ（処理の回数は今の値）
            assertThat(publications.stoppedOf(third.id())).hasValueSatisfying(stopped ->
                    assertThat(stopped.completionAttempts()).isEqualTo(3));
            assertThat(publications.stoppedOf(second.id())).hasValueSatisfying(stopped ->
                    assertThat(stopped.completionAttempts()).isEqualTo(2));
            assertThat(logsOf(thirdPublication)).singleElement().satisfies(entry -> {
                assertThat(entry.level()).isEqualTo(Level.ERROR);
                assertThat(entry.text()).contains("処理の回数 3");
            });
            assertThat(logsOf(secondPublication)).singleElement().satisfies(entry -> {
                assertThat(entry.level()).isEqualTo(Level.ERROR);
                assertThat(entry.text()).contains("処理の回数 2");
            });
        }

        @Test
        @DisplayName("AC-290 処理の回数の上限を下げても、処理の回数が新しい上限より少ない失敗したイベントは移さない")
        void doesNotMoveFailedEventBelowLoweredMaxAttempts() {
            // 準備：処理の回数の上限 3 のジョブで、処理を 1 回失敗したイベント
            var event = failedUntil(jobWithMaxAttempts(3), 1);

            // 操作：上限を 2 に下げ、何も再発行しない（発行からの時間の下限が長い）ジョブを、1 分後に動かす
            clock.advance(Duration.ofMinutes(1));
            jobWith(Duration.ofDays(365), 2).resubmit();

            // 確認：止めたイベントの表になく、イベント発行記録の表に、処理の回数 1 のまま残る
            assertThat(publications.stoppedOf(event.id())).isEmpty();
            assertThat(publicationOf(event).getCompletionAttempts()).isEqualTo(1);
        }

        @Test
        @DisplayName("AC-290 処理の回数が上限ちょうどの処理で成功したイベントは、次の回で止めない")
        void doesNotStopEventCompletedAtMaxAttempts() {
            // 準備：処理の回数の上限 2 のジョブで、1 回目の処理に失敗し、再発行の 2 回目の処理で終えたイベント
            var job = jobWithMaxAttempts(2);
            var event = failedOnce();
            clock.advance(Duration.ofMinutes(6));
            job.resubmit();
            asyncEvents.awaitHandled(event::equals);

            // 操作：1 分後に再発行する
            clock.advance(Duration.ofMinutes(1));
            job.resubmit();

            // 確認：止めたイベントの表になく、イベント発行記録の表に、処理の回数 2 の記録が残り、リスナーは 2 回だけ受けた
            assertThat(publications.stoppedOf(event.id())).isEmpty();
            assertThat(publications.count(event.id())).isEqualTo(1);
            assertThat(listener.attempts(event)).isEqualTo(2);
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
            var publication = publicationOf(event);

            // 操作：次の回で止める
            logs.clear();
            resubmitTwice(job);

            // 確認：イベントの中身（メールアドレス）が、止めたログにも、ほかのログにもない
            assertThat(logsOf(publication)).singleElement()
                    .satisfies(entry -> assertThat(entry.text()).doesNotContain(event.email()));
            assertThat(logs.text()).doesNotContain(event.email());
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"PUBLISHED", "PROCESSING", "RESUBMITTED"})
        @DisplayName("AC-291 最後の再発行が処理中（失敗も完了もしていない）のイベントは、止めない")
        void doesNotStopEventInProcess(String status) {
            // 準備：処理を 2 回失敗したイベントを、結果の出ていない状態にする
            var job = jobWithMaxAttempts(2);
            var event = failedUntil(job, 2);
            var publication = publicationOf(event);
            markStatus(event, status);

            // 操作：1 分後に再発行する
            clock.advance(Duration.ofMinutes(1));
            logs.clear();
            job.resubmit();

            // 確認：イベント発行記録の表に、状態と処理の回数が変わらないまま残り、止めたイベントの表になく、この発行の記録のログがない
            assertThat(statusOf(publication)).isEqualTo(status);
            assertThat(publicationOf(event).getCompletionAttempts()).isEqualTo(2);
            assertThat(publications.stoppedOf(event.id())).isEmpty();
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

            // 確認：止めたイベントの表に移ってイベント発行記録の表から消え、この発行の記録のエラーのログが 1 件
            assertThat(publications.stoppedOf(event.id())).hasValueSatisfying(stopped ->
                    assertThat(stopped.completionAttempts()).isEqualTo(2));
            assertThat(publications.count(event.id())).isZero();
            assertThat(logsOf(publication)).filteredOn(entry -> entry.level() == Level.ERROR).hasSize(1);
        }

        @Test
        @DisplayName("AC-290 止めたイベントは、処理の回数の上限を上げたジョブでも、再発行のために読まない")
        void doesNotReadStoppedEvent() {
            // 準備：処理の回数の上限 2 のジョブで止めたイベント
            var event = stoppedEvent();
            var before = publications.stoppedOf(event.id()).orElseThrow();

            // 操作：6 分後に、処理の回数の上限 5 のジョブで再発行する
            clock.advance(Duration.ofMinutes(6));
            jobWithMaxAttempts(5).resubmit();

            // 確認：止めたイベントの表の行が変わらず、イベント発行記録の表に戻らず、リスナーは 2 回だけ受けた
            assertThat(publications.stoppedOf(event.id())).contains(before);
            assertThat(publications.count(event.id())).isZero();
            assertThat(failingListener.attempts(event)).isEqualTo(2);
        }

        @Test
        @DisplayName("AC-293 止めたイベントは、処理の済んだイベントの片付けで消えない")
        void stoppedEventIsNotCleanedUp() {
            // 準備：処理の回数の上限 2 のジョブで止めたイベント
            var event = stoppedEvent();
            var before = publications.stoppedOf(event.id()).orElseThrow();

            // 操作：30 日後に片付ける
            clock.advance(Duration.ofDays(30));
            cleanupJob.cleanUp();

            // 確認：止めたイベントの表の行が、そのまま残る
            assertThat(publications.stoppedOf(event.id())).contains(before);
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

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"PUBLISHED", "PROCESSING", "RESUBMITTED"})
        @DisplayName("処理の結果が出る前にインスタンスが止まったイベントは、最後の試行から 5 分たつと再発行される")
        void resubmitsEventStuckBeforeResult(String status) {
            // 準備：処理を 2 回失敗したイベントを、処理の結果が出る前に止まった状態にする
            var job = jobWithMaxAttempts(3);
            var event = failedUntil(job, 2);
            markStatus(event, status);

            // 操作：6 分後に、結果の出ないイベントを失敗の扱いにしてから、再発行する
            clock.advance(Duration.ofMinutes(6));
            registry.markStalePublicationsFailed(staleness);
            job.resubmit();

            // 確認：3 回目の処理が動く
            asyncEvents.awaitFailed(event::equals, 3);
            assertThat(failingListener.attempts(event)).isEqualTo(3);
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"PROCESSING", "RESUBMITTED"})
        @DisplayName("最後の試行から 5 分たっていない処理中のイベントは、失敗の扱いにせず、再発行もしない")
        void doesNotFailRecentEventInProcess(String status) {
            // 準備：処理を 2 回失敗したイベントを、処理中にする
            var job = jobWithMaxAttempts(3);
            var event = failedUntil(job, 2);
            var lastResubmission = publicationOf(event).getLastResubmissionDate();
            markStatus(event, status);

            // 操作：4 分後に、結果の出ないイベントを失敗の扱いにしてから、再発行する
            clock.advance(Duration.ofMinutes(4));
            registry.markStalePublicationsFailed(staleness);
            job.resubmit();

            // 確認：処理中のままで、処理の回数と最後の再発行の日時が変わらない
            var publication = publicationOf(event);
            assertThat(statusOf(publication)).isEqualTo(status);
            assertThat(publication.getCompletionAttempts()).isEqualTo(2);
            assertThat(publication.getLastResubmissionDate()).isEqualTo(lastResubmission);
        }
    }

    @Nested
    @DisplayName("件数のメトリクス")
    class Metrics {

        @Test
        @DisplayName("AC-292 再発行のジョブの後、未完了のイベントの件数と止めたイベントの件数をメトリクスに出す")
        void publishesCountsAsMetrics() {
            // 準備：何も再発行しないジョブ（発行からの時間の下限が長く、処理の回数の上限が既定）で、
            // ほかのテストが残したイベントの件数を、作る前に数える（統合テストは DB を共有するので、作った数を差分で比べる）
            var quietJob = jobWith(Duration.ofDays(365), 60);
            quietJob.resubmit();
            var incompleteBefore = gauge("ecsite.events.incomplete");
            var stoppedBefore = gauge("ecsite.events.stopped");
            // 最後の処理に失敗したイベント 1 件
            publications.failedOnce(failingEvent("metrics-failed"));
            // 止めるイベント 1 件（ほかのテストのイベントを再発行も停止もしないよう、処理の回数を上限 60 に書き換え、次のジョブで止める）
            var stopped = publications.failedOnce(failingEvent("metrics-stopped"));
            markAttempts(stopped, 60);
            // 処理を終えたイベント 1 件
            var completed = publications.failedOnce(new FlakyEvent(UUID.randomUUID()));
            clock.advance(Duration.ofMinutes(6));
            asyncEvents.resubmit(completed::equals);
            asyncEvents.awaitHandled(completed::equals);

            // 操作：ジョブを動かす
            quietJob.resubmit();

            // 確認：止めた件数は 1 件増え、未完了の件数は、失敗したイベントと止めたイベントの 2 件増える（処理を終えたイベントは数えない）
            assertThat(publications.stoppedOf(stopped.id())).isPresent();
            assertThat(gauge("ecsite.events.stopped")).isEqualTo(stoppedBefore + 1);
            assertThat(gauge("ecsite.events.incomplete")).isEqualTo(incompleteBefore + 2);
        }
    }

    @Nested
    @DisplayName("止めたイベントの復旧")
    class Recovery {

        @Test
        @DisplayName("止めたイベントを復旧の手順で戻すと、次の回で再発行され、リスナーが受ける")
        void resubmitsRestoredEvent() {
            // 準備：処理の回数の上限 2 のジョブで止めたイベント
            var job = jobWithMaxAttempts(2);
            var event = stoppedEvent(job);
            var stopped = publications.stoppedOf(event.id()).orElseThrow();

            // 操作：復旧の手順で戻す
            dsl.execute(RESTORE_STOPPED_EVENT, stopped.id());

            // 確認：止めたイベントの表になく、イベント発行記録の表に、失敗の状態・処理の回数 1・未完了で戻る
            assertThat(publications.stoppedOf(event.id())).isEmpty();
            var restored = publicationOf(event);
            assertThat(restored.getIdentifier()).isEqualTo(stopped.id());
            assertThat(restored.getStatus()).isEqualTo(EventPublication.Status.FAILED);
            assertThat(restored.getCompletionAttempts()).isEqualTo(1);
            assertThat(restored.getCompletionDate()).isEmpty();

            // 操作：1 分後に再発行する
            clock.advance(Duration.ofMinutes(1));
            job.resubmit();

            // 確認：リスナーが 3 回目を受け、処理の回数が 2 になる
            asyncEvents.awaitFailed(event::equals, 2);
            assertThat(failingListener.attempts(event)).isEqualTo(3);
        }

        @Test
        @DisplayName("不要な止めたイベントを復旧の手順で消すと、止めたイベントの表からも、イベント発行記録の表からもなくなる")
        void deletesUnneededStoppedEvent() {
            // 準備：止めたイベント
            var event = stoppedEvent();
            var stopped = publications.stoppedOf(event.id()).orElseThrow();

            // 操作：復旧の手順で消す
            dsl.execute(DELETE_STOPPED_EVENT, stopped.id());

            // 確認：どちらの表にもない
            assertThat(publications.stoppedOf(event.id())).isEmpty();
            assertThat(publications.count(event.id())).isZero();
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 処理の回数の上限 2 のジョブで、処理を 2 回失敗させてから止めたイベントを作る。
     *
     * @return 止めたイベント
     */
    private FailingEvent stoppedEvent() {
        return stoppedEvent(jobWithMaxAttempts(2));
    }

    /**
     * 再発行のジョブで、処理の回数が上限になるまで失敗させてから止めたイベントを作る。
     *
     * @param job 処理の回数の上限 2 の再発行のジョブ
     * @return 止めたイベント
     */
    private FailingEvent stoppedEvent(EventResubmissionJob job) {
        var event = failedUntil(job, 2);
        clock.advance(Duration.ofMinutes(1));
        job.resubmit();
        return event;
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
        var event = publications.failedOnce(failingEvent("budget"));
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
     * イベントの記録の処理の回数を書き換え、処理の回数が上限に達した状態に見せる。
     *
     * @param event    発行したイベント
     * @param attempts 処理の回数
     */
    private void markAttempts(Object event, int attempts) {
        dsl.update(EVENT_PUBLICATION).set(COMPLETION_ATTEMPTS, attempts)
                .where(ID.eq(publicationOf(event).getIdentifier())).execute();
    }

    /**
     * イベントの記録の、処理の状態の列の値を返す。
     *
     * @param publication 発行の記録
     * @return 処理の状態
     */
    private String statusOf(TargetEventPublication publication) {
        return dsl.select(STATUS).from(EVENT_PUBLICATION).where(ID.eq(publication.getIdentifier())).fetchOne(STATUS);
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
        return publications.failedUntil(failingEvent("stopped"), job, completionAttempts);
    }

    /**
     * いつも失敗するテストのイベントを作る（発行はしない）。
     *
     * @param prefix メールアドレスの先頭（どのテストのイベントかを見分ける）
     * @return テストのイベント
     */
    private static FailingEvent failingEvent(String prefix) {
        return new FailingEvent(UUID.randomUUID(), prefix + "-" + UUID.randomUUID() + "@example.com");
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

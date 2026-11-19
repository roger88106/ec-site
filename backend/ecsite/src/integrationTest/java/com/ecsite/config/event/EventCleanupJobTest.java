package com.ecsite.config.event;

import com.ecsite.support.LogCapture;
import com.ecsite.support.MutableClock;
import com.ecsite.support.async.AsyncEvents;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.config.TestClockConfig;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.ecsite.config.event.EventPublications.COMPLETION_DATE;
import static com.ecsite.config.event.EventPublications.EVENT_PUBLICATION;
import static com.ecsite.config.event.EventPublications.STATUS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 処理の済んだイベントの片付けの統合テスト
 *
 * <p>イベントはコミットの後に非同期で処理されるので、処理の記録ができるのを待つ。
 * 処理を終えた時刻はアプリの時計で決まるので、時計を決めてから発行する。
 */
@IntegrationTest
class EventCleanupJobTest {

    // ============================== 定数 ==============================

    /**
     * イベントを発行する時刻（統合テストの時計の初期値）
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = TestClockConfig.INITIAL_INSTANT;

    /**
     * 処理の済んだイベントの保存期間（設定の既定）
     */
    private static final Duration RETENTION = Duration.ofDays(7);

    /**
     * 保存期間の内に処理を終えたイベントの発行の時刻（NOW の 2 日後）
     */
    private static final Instant RECENT = NOW.plus(Duration.ofDays(2));

    /**
     * 片付けの時刻（NOW に処理を終えたイベントの保存期間を、1 時間過ぎた時刻）
     */
    private static final Instant CLEANUP_AT = NOW.plus(RETENTION).plus(Duration.ofHours(1));

    // ============================== Bean ==============================

    /**
     * 片付けのジョブが出したログ
     */
    @RegisterExtension
    final LogCapture logs = LogCapture.of(EventCleanupJob.class);

    /**
     * テストするジョブ（設定の既定の保存期間）
     */
    @Autowired
    EventCleanupJob job;

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
     * DB を読む jOOQ
     */
    @Autowired
    DSLContext dsl;

    /**
     * イベントの発行の記録
     */
    @Autowired
    EventPublicationRegistry registry;

    /**
     * イベントの記録（消す件数の上限を指定して消す）
     */
    @Autowired
    EventPublicationRecords records;

    /**
     * 非同期のリスナーを待つ補助
     */
    @Autowired
    AsyncEvents asyncEvents;

    /**
     * イベントの発行を確認する補助
     */
    EventPublications publications;

    @BeforeEach
    void setUp() {
        publications = new EventPublications(dsl, transactionTemplate, eventPublisher, registry, asyncEvents, clock);
    }

    // ============================== テスト ==============================

    @Test
    @DisplayName("AC-284 処理を終えてから保存期間（7 日）を過ぎたイベントは消える")
    void deletesEventCompletedBeforeRetention() {
        // 準備：NOW に処理を終えたイベント
        var old = publish(new CleanupEvent(UUID.randomUUID(), false));

        // 操作：保存期間を過ぎてから片付ける
        clock.setInstant(CLEANUP_AT);
        job.cleanUp();

        // 確認：イベントの記録がない
        assertThat(publicationCount(old)).isZero();
    }

    @Test
    @DisplayName("AC-284 処理を終えてから保存期間（7 日）を過ぎていないイベントは残る")
    void keepsEventCompletedWithinRetention() {
        // 準備：NOW の 2 日後に処理を終えたイベント
        clock.setInstant(RECENT);
        var recent = publish(new CleanupEvent(UUID.randomUUID(), false));

        // 操作：NOW に処理を終えたものの保存期間を過ぎた時刻に片付ける
        clock.setInstant(CLEANUP_AT);
        job.cleanUp();

        // 確認：イベントの記録が残る
        assertThat(publicationCount(recent)).isEqualTo(1);
    }

    @Test
    @DisplayName("処理を終えてからちょうど保存期間（7 日）のイベントは、まだ残る")
    void keepsEventCompletedExactlyRetentionAgo() {
        // 準備：NOW に処理を終えたイベント
        var event = publish(new CleanupEvent(UUID.randomUUID(), false));

        // 操作：ちょうど 7 日後に片付ける
        clock.setInstant(NOW.plus(RETENTION));
        job.cleanUp();

        // 確認：イベントの記録が残る
        assertThat(publicationCount(event)).isEqualTo(1);
    }

    @Test
    @DisplayName("処理を終えてから保存期間（7 日）を 1 マイクロ秒でも過ぎたイベントは消える")
    void deletesEventJustAfterRetention() {
        // 準備：NOW に処理を終えたイベント
        var event = publish(new CleanupEvent(UUID.randomUUID(), false));

        // 操作：7 日と 1 マイクロ秒後に片付ける
        clock.setInstant(NOW.plus(RETENTION).plus(Duration.ofNanos(1_000)));
        job.cleanUp();

        // 確認：イベントの記録がない
        assertThat(publicationCount(event)).isZero();
    }

    @Test
    @DisplayName("状態の列のない（古い形式の）処理を終えたイベントも、保存期間を過ぎたら消える")
    void deletesCompletedEventWithoutStatus() {
        // 準備：NOW に処理を終え、状態の列を空にしたイベント
        var event = publish(new CleanupEvent(UUID.randomUUID(), false));
        publications.clearStatus(event);

        // 操作：保存期間を過ぎてから片付ける
        clock.setInstant(CLEANUP_AT);
        job.cleanUp();

        // 確認：イベントの記録がない
        assertThat(publicationCount(event)).isZero();
    }

    @Test
    @DisplayName("AC-286 処理を終えていないイベントは、保存期間を過ぎても残る")
    void keepsIncompleteEvent() {
        // 準備：NOW にリスナーが失敗したイベント
        var incomplete = publish(new CleanupEvent(UUID.randomUUID(), true));

        // 操作：保存期間を過ぎてから片付ける
        clock.setInstant(CLEANUP_AT);
        job.cleanUp();

        // 確認：イベントの記録が残る
        assertThat(publicationCount(incomplete)).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-288 消した件数をログに出す")
    void logsDeletedCount() {
        // 準備：NOW に処理を終えたイベント
        publish(new CleanupEvent(UUID.randomUUID(), false));

        // 準備：巻き戻せないので、ほかのテストの処理の済んだ古いイベントも消える。消す前に数えて、ログの件数と比べる
        clock.setInstant(CLEANUP_AT);
        var expectedDeleted = dsl.fetchCount(EVENT_PUBLICATION,
                COMPLETION_DATE.lt(CLEANUP_AT.minus(RETENTION)).and(STATUS.eq("COMPLETED").or(STATUS.isNull())));

        // 操作：片付ける
        logs.clear();
        job.cleanUp();

        // 確認：消す前に数えた件数（このテストのイベントを含むので 1 件以上）をログに出す
        assertThat(expectedDeleted).isPositive();
        assertThat(logs.messages(Level.INFO)).contains("処理の済んだイベントを消した：" + expectedDeleted + " 件");
    }

    @Test
    @DisplayName("1 回に消す件数は、指定した上限までである（件数の上限を置かない 1 文で、文の実行のタイムアウトを超えないようにする）")
    void deletesAtMostBatchSize() {
        // 準備：NOW に処理を終えたイベントを 3 件
        publish(new CleanupEvent(UUID.randomUUID(), false));
        publish(new CleanupEvent(UUID.randomUUID(), false));
        publish(new CleanupEvent(UUID.randomUUID(), false));

        // 操作：保存期間を過ぎてから、上限 2 件で消す
        clock.setInstant(CLEANUP_AT);
        var deleted = records.deleteCompletedBefore(CLEANUP_AT.minus(RETENTION), 2);

        // 確認：消す対象は 3 件以上あるが、消したのは上限の 2 件
        assertThat(deleted).isEqualTo(2);
    }

    // ============================== 準備の補助 ==============================

    /**
     * イベントをコミットするトランザクションで発行し、リスナーの処理が終わるのを待つ。
     *
     * @param event 発行するイベント
     * @return イベント ID
     */
    private UUID publish(CleanupEvent event) {
        return publications.publishAndAwait(event, event.fail()).id();
    }

    /**
     * イベントの記録の数を返す。
     *
     * @param eventId イベント ID
     * @return 記録の数
     */
    private int publicationCount(UUID eventId) {
        return publications.count(eventId);
    }

    /**
     * テストのイベント
     *
     * @param id   イベントを見分ける ID
     * @param fail リスナーを失敗させるか
     */
    record CleanupEvent(UUID id, boolean fail) {
    }

    /**
     * テストのリスナーを登録する設定
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        CleanupListener cleanupListener() {
            return new CleanupListener();
        }
    }

    /**
     * 指定したイベントだけを失敗させるリスナー
     */
    static class CleanupListener {

        @ApplicationModuleListener
        void on(CleanupEvent event) {
            if (event.fail()) {
                throw new IllegalStateException("失敗させる");
            }
        }
    }
}

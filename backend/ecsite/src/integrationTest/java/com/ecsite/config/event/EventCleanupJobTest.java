package com.ecsite.config.event;

import com.ecsite.AsyncEvents;
import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.TestClockConfig;
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
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 処理の済んだイベントの片付けの統合テスト（T017-01-07、AC-284、286、288）。<br />
 * イベントはコミットの後に非同期で処理されるので、処理の記録ができるのを待つ。
 * 処理を終えた時刻はアプリの時計で決まるので、時計を決めてから発行する。
 */
@IntegrationTest
@Import(TestClockConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class EventCleanupJobTest {

    /**
     * イベントを発行する時刻
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    @Autowired
    EventCleanupJob job;

    @Autowired
    MutableClock clock;

    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    DSLContext dsl;

    @Autowired
    EventPublicationRegistry registry;

    @Autowired
    AsyncEvents asyncEvents;

    EventPublications publications;

    @BeforeEach
    void setUp() {
        clock.setInstant(NOW);
        publications = new EventPublications(dsl, transactionTemplate, eventPublisher, registry, asyncEvents, clock);
    }

    @Test
    @DisplayName("AC-284 処理を終えてから保存期間（7 日）より前のイベントは消え、その後のものと未完了のイベントは残る（AC-286）。消した件数をログに出す（AC-288）")
    void deletesEventsCompletedBeforeRetention(CapturedOutput output) {
        var old = publish(new CleanupEvent(UUID.randomUUID(), false));
        var incomplete = publish(new CleanupEvent(UUID.randomUUID(), true));
        clock.setInstant(NOW.plus(Duration.ofDays(2)));
        var recent = publish(new CleanupEvent(UUID.randomUUID(), false));

        clock.setInstant(NOW.plus(Duration.ofDays(7)).plus(Duration.ofHours(1)));
        job.cleanUp();

        assertThat(publicationCount(old)).isZero();
        assertThat(publicationCount(recent)).isEqualTo(1);
        assertThat(publicationCount(incomplete)).isEqualTo(1);
        assertThat(output).containsPattern("処理の済んだイベントを消した：[1-9][0-9]* 件");
    }

    /**
     * イベントをコミットするトランザクションで発行し、リスナーの処理が終わるのを待つ。
     *
     * @return イベント ID
     */
    private UUID publish(CleanupEvent event) {
        return publications.publishAndAwait(event, event.fail()).id();
    }

    /**
     * イベントの記録の数
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
     * テストのリスナーを登録する。
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

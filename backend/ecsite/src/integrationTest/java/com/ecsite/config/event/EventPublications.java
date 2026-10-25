package com.ecsite.config.event;

import com.ecsite.AsyncEvents;
import com.ecsite.MutableClock;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.modulith.events.core.TargetEventPublication;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.awaitility.Awaitility.await;

/**
 * イベントの記録（Spring Modulith の表）のテーブルの定義と、イベントを発行して処理を待つ補助。<br />
 * イベントの片付けと再発行のテストが使う。処理は非同期なので、リスナーが終わるのを待つ処理は {@link AsyncEvents} に任せる。
 */
class EventPublications {

    /**
     * イベントの記録のテーブル（jOOQ の生成クラスはモジュールの外では使わない）
     */
    static final Table<?> EVENT_PUBLICATION = DSL.table(DSL.name("common", "event_publication"));

    /**
     * イベントの記録の ID
     */
    static final Field<UUID> ID = DSL.field(DSL.name("id"), UUID.class);

    /**
     * 処理の状態
     */
    static final Field<String> STATUS = DSL.field(DSL.name("status"), String.class);

    /**
     * 処理の回数
     */
    static final Field<Integer> COMPLETION_ATTEMPTS = DSL.field(DSL.name("completion_attempts"), Integer.class);

    /**
     * 処理を終えた時刻
     */
    static final Field<Instant> COMPLETION_DATE = DSL.field(DSL.name("completion_date"), Instant.class);

    /**
     * イベントを JSON にして持つ列
     */
    private static final Field<String> SERIALIZED_EVENT = DSL.field(DSL.name("serialized_event"), String.class);

    private final DSLContext dsl;

    private final TransactionTemplate transactionTemplate;

    private final ApplicationEventPublisher eventPublisher;

    private final EventPublicationRegistry registry;

    private final AsyncEvents asyncEvents;

    private final MutableClock clock;

    EventPublications(DSLContext dsl, TransactionTemplate transactionTemplate, ApplicationEventPublisher eventPublisher,
                      EventPublicationRegistry registry, AsyncEvents asyncEvents, MutableClock clock) {
        this.dsl = dsl;
        this.transactionTemplate = transactionTemplate;
        this.eventPublisher = eventPublisher;
        this.registry = registry;
        this.asyncEvents = asyncEvents;
        this.clock = clock;
    }

    /**
     * イベントをコミットするトランザクションで発行し、リスナーの処理が終わる（成功か失敗）のを待つ。
     *
     * @param failing リスナーが失敗するイベントか
     * @return 発行したイベント
     */
    <E> E publishAndAwait(E event, boolean failing) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
        if (failing) {
            asyncEvents.awaitFailed(event::equals);
        } else {
            asyncEvents.awaitHandled(event::equals);
        }
        return event;
    }

    /**
     * イベントを発行し、リスナーの 1 回目の失敗を待つ。
     *
     * @return 発行したイベント
     */
    <E> E failedOnce(E event) {
        return publishAndAwait(event, true);
    }

    /**
     * いつも失敗するイベントを発行し、処理の回数が指定の回数になるまで再発行する（最後の処理も失敗する）。
     *
     * @return 発行したイベント
     */
    <E> E failedUntil(E event, EventResubmissionJob job, int completionAttempts) {
        failedOnce(event);
        clock.advance(Duration.ofMinutes(5));
        for (int attempts = 2; attempts <= completionAttempts; attempts++) {
            clock.advance(Duration.ofMinutes(1));
            job.resubmit();
            int expected = attempts;
            await().atMost(AsyncEvents.TIMEOUT).until(() -> failedWith(event, expected));
        }
        return event;
    }

    /**
     * テストのイベントの発行の記録が、指定の処理の回数で、処理に失敗した状態か。
     */
    boolean failedWith(Object event, int completionAttempts) {
        var publication = publicationOf(event);
        return publication.getStatus() == EventPublication.Status.FAILED
                && publication.getCompletionAttempts() == completionAttempts;
    }

    /**
     * テストのイベントの、未完了の発行の記録
     */
    TargetEventPublication publicationOf(Object event) {
        return registry.findIncompletePublications().stream()
                .filter(publication -> publication.getEvent().equals(event))
                .findFirst().orElseThrow();
    }

    /**
     * イベント ID を含む記録の数（記録はイベントを JSON にして持つ）
     */
    int count(UUID eventId) {
        return dsl.fetchCount(EVENT_PUBLICATION, SERIALIZED_EVENT.contains(eventId.toString()));
    }
}

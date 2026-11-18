package com.ecsite.config.event;

import com.ecsite.support.MutableClock;
import com.ecsite.support.async.AsyncEvents;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.modulith.events.core.TargetEventPublication;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * イベントの記録（Spring Modulith の表）と止めたイベントの表のテーブルの定義と、イベントを発行して処理を待つ補助
 *
 * <p>イベントの片付けと再発行のテストが使う。処理は非同期なので、リスナーが終わるのを待つ処理は {@link AsyncEvents} に任せる。
 */
class EventPublications {

    /**
     * イベントの記録のテーブル
     *
     * <p>jOOQ の生成クラスはモジュールの外では使わない。
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

    /**
     * 止めたイベントのテーブル
     */
    private static final Table<?> EVENT_PUBLICATION_STOPPED = DSL.table(DSL.name("common", "event_publication_stopped"));

    /**
     * リスナーの ID
     */
    private static final Field<String> LISTENER_ID = DSL.field(DSL.name("listener_id"), String.class);

    /**
     * イベントの型
     */
    private static final Field<String> EVENT_TYPE = DSL.field(DSL.name("event_type"), String.class);

    /**
     * 最後の再発行の時刻
     */
    private static final Field<Instant> LAST_RESUBMISSION_DATE = DSL.field(DSL.name("last_resubmission_date"), Instant.class);

    /**
     * 止めた時刻
     */
    private static final Field<Instant> STOPPED_DATE = DSL.field(DSL.name("stopped_date"), Instant.class);

    /**
     * イベントの記録の表を数える DSLContext
     */
    private final DSLContext dsl;

    /**
     * イベントを、コミットするトランザクションで発行するためのテンプレート
     */
    private final TransactionTemplate transactionTemplate;

    /**
     * イベントの発行
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 未完了のイベントの発行の記録
     */
    private final EventPublicationRegistry registry;

    /**
     * リスナーの処理を待つ補助
     */
    private final AsyncEvents asyncEvents;

    /**
     * テスト用の時計
     *
     * <p>再発行の対象になる時間まで進める。
     */
    private final MutableClock clock;

    /**
     * コンストラクタ
     *
     * @param dsl                 DSLContext
     * @param transactionTemplate トランザクションのテンプレート
     * @param eventPublisher      イベントの発行
     * @param registry            イベントの発行の記録
     * @param asyncEvents         リスナーの処理を待つ補助
     * @param clock               テスト用の時計
     */
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
     * @param <E>     イベントの型
     * @param event   発行するイベント
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
     * @param <E>   イベントの型
     * @param event 発行するイベント
     * @return 発行したイベント
     */
    <E> E failedOnce(E event) {
        return publishAndAwait(event, true);
    }

    /**
     * いつも失敗するイベントを発行し、処理の回数が指定の回数になるまで再発行する（最後の処理も失敗する）。
     *
     * @param <E>                イベントの型
     * @param event              発行するイベント
     * @param job                再発行のジョブ
     * @param completionAttempts 処理の回数
     * @return 発行したイベント
     */
    <E> E failedUntil(E event, EventResubmissionJob job, int completionAttempts) {
        failedOnce(event);
        clock.advance(Duration.ofMinutes(5));
        for (int attempts = 2; attempts <= completionAttempts; attempts++) {
            clock.advance(Duration.ofMinutes(1));
            job.resubmit();
            asyncEvents.awaitFailed(event::equals, attempts);
        }
        return event;
    }

    /**
     * テストのイベントの、未完了の発行の記録。
     *
     * @param event 発行したイベント
     * @return 未完了の発行の記録
     */
    TargetEventPublication publicationOf(Object event) {
        return registry.findIncompletePublications().stream()
                .filter(publication -> publication.getEvent().equals(event))
                .findFirst().orElseThrow();
    }

    /**
     * イベント ID を含む記録の数（記録はイベントを JSON にして持つ）。
     *
     * @param eventId イベントを見分ける ID
     * @return 記録の数
     */
    int count(UUID eventId) {
        return dsl.fetchCount(EVENT_PUBLICATION, SERIALIZED_EVENT.contains(eventId.toString()));
    }

    /**
     * イベント ID を含む、止めたイベントの表の行。
     *
     * @param eventId イベントを見分ける ID
     * @return 止めたイベント（止めたイベントの表になければ空）
     */
    Optional<StoppedEvent> stoppedOf(UUID eventId) {
        return dsl.select(ID, LISTENER_ID, EVENT_TYPE, COMPLETION_ATTEMPTS, LAST_RESUBMISSION_DATE, STOPPED_DATE)
                .from(EVENT_PUBLICATION_STOPPED)
                .where(SERIALIZED_EVENT.contains(eventId.toString()))
                .fetchOptional(row -> new StoppedEvent(row.get(ID), row.get(LISTENER_ID), row.get(EVENT_TYPE),
                        row.get(COMPLETION_ATTEMPTS), row.get(LAST_RESUBMISSION_DATE), row.get(STOPPED_DATE)));
    }

    /**
     * 止めたイベントの表の行の数。
     *
     * @return 行の数
     */
    int countStopped() {
        return dsl.fetchCount(EVENT_PUBLICATION_STOPPED);
    }

    /**
     * 止めたイベントの表の行
     *
     * @param id                   イベントの記録の ID
     * @param listenerId           リスナーの ID
     * @param eventType            イベントの型
     * @param completionAttempts   処理の回数
     * @param lastResubmissionDate 最後の再発行の時刻
     * @param stoppedDate          止めた時刻
     */
    record StoppedEvent(UUID id, String listenerId, String eventType, int completionAttempts,
                        Instant lastResubmissionDate, Instant stoppedDate) {
    }

    /**
     * イベント ID を含む記録の状態の列を空にする（状態の列のない、古い形式の記録に見せる）。
     *
     * @param eventId イベントを見分ける ID
     */
    void clearStatus(UUID eventId) {
        dsl.update(EVENT_PUBLICATION).set(STATUS, (String) null)
                .where(SERIALIZED_EVENT.contains(eventId.toString())).execute();
    }
}

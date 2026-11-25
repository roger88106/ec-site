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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * イベントの記録（Spring Modulith の表）と止めたイベントの表の定義と、イベントを発行して処理を待つ補助
 *
 * <p>イベントの片付けと再発行のテストが使う。処理は非同期なので、リスナーが終わるのを待つ処理は {@link AsyncEvents} に任せる。
 */
class EventPublications {

    /**
     * イベント発行記録のテーブル
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
     * 発行の時刻
     */
    private static final Field<Instant> PUBLICATION_DATE = DSL.field(DSL.name("publication_date"), Instant.class);

    /**
     * 最後の再発行の時刻
     */
    private static final Field<Instant> LAST_RESUBMISSION_DATE = DSL.field(DSL.name("last_resubmission_date"), Instant.class);

    /**
     * 止めた時刻
     */
    private static final Field<Instant> STOPPED_DATE = DSL.field(DSL.name("stopped_date"), Instant.class);

    /**
     * 記録の行を、新しい ID で複製する SQL（複製する数と、元の行の ID を bind 変数で渡す）
     *
     * <p>リスナーが受けられる JSON を含めて、列を元の行のままにする。1 件ずつイベントを発行するより速い。
     */
    private static final String COPY_PUBLICATION = """
            INSERT INTO common.event_publication
                (id, listener_id, event_type, serialized_event, publication_date,
                 completion_date, status, completion_attempts, last_resubmission_date)
            SELECT gen_random_uuid(), source.listener_id, source.event_type, source.serialized_event, source.publication_date,
                   source.completion_date, source.status, source.completion_attempts, source.last_resubmission_date
            FROM common.event_publication source, generate_series(1, ?)
            WHERE source.id = ?
            """;

    /**
     * イベント発行記録の表を数える DSLContext
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
     * 本番の、イベントの記録を読む操作
     */
    private final EventPublicationRecords records;

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
     * @param records             本番の、イベントの記録を読む操作
     * @param asyncEvents         リスナーの処理を待つ補助
     * @param clock               テスト用の時計
     */
    EventPublications(DSLContext dsl, TransactionTemplate transactionTemplate, ApplicationEventPublisher eventPublisher,
                      EventPublicationRegistry registry, EventPublicationRecords records, AsyncEvents asyncEvents,
                      MutableClock clock) {
        this.dsl = dsl;
        this.transactionTemplate = transactionTemplate;
        this.eventPublisher = eventPublisher;
        this.registry = registry;
        this.records = records;
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
     * 発行したイベントの未完了の記録を、新しい ID で指定の数だけ複製する。
     *
     * <p>列は元の記録と同じ（発行の日時、処理の回数、最後の再発行の日時、状態、イベントの JSON）。
     * 同じイベントを受けるリスナー宛の、同じ状態の記録を、イベントを発行せずに多く作るときに使う。
     *
     * @param event 発行したイベント
     * @param count 複製する数
     */
    void copyPublication(Object event, int count) {
        dsl.execute(COPY_PUBLICATION, count, publicationOf(event).getIdentifier());
    }

    /**
     * イベント ID を含む記録を、全て、指定の時刻に再発行して失敗した状態にする（処理の回数を 1 増やす）。
     *
     * @param eventId       イベントを見分ける ID
     * @param resubmittedAt 再発行した時刻
     */
    void markFailedAfterResubmission(UUID eventId, Instant resubmittedAt) {
        dsl.update(EVENT_PUBLICATION)
                .set(LAST_RESUBMISSION_DATE, resubmittedAt)
                .set(COMPLETION_ATTEMPTS, COMPLETION_ATTEMPTS.plus(1))
                .set(STATUS, "FAILED")
                .where(SERIALIZED_EVENT.contains(eventId.toString()))
                .execute();
    }

    /**
     * 再発行するイベントの記録の ID を、本番の選び方で選ぶ。
     *
     * @param publishedBefore 発行の時刻の上限
     * @param lastTriedBy     最後の試行の時刻の上限
     * @param maxAttempts     処理の回数の上限
     * @param limit           選ぶ件数の上限
     * @return 再発行するイベントの記録の ID（最後の試行の古い順）
     */
    List<UUID> findResubmissionTargets(Instant publishedBefore, Instant lastTriedBy, int maxAttempts, int limit) {
        return records.findResubmissionTargets(publishedBefore, lastTriedBy, maxAttempts, limit);
    }

    /**
     * 再発行中の記録の件数（ほかのテストの行を含む）。
     *
     * @return 再発行中の記録の件数
     */
    int countResubmitted() {
        return records.countResubmitted();
    }

    /**
     * イベント ID を含む記録を、全て消す（複製した記録が、ほかのテストの再発行の枠を使わないように片付ける）。
     *
     * @param eventId イベントを見分ける ID
     */
    void deleteAll(UUID eventId) {
        dsl.deleteFrom(EVENT_PUBLICATION).where(SERIALIZED_EVENT.contains(eventId.toString())).execute();
    }

    /**
     * イベント ID を含む記録を、全て、指定の状態にする（再発行中に見せるときなどに使う）。
     *
     * @param eventId イベントを見分ける ID
     * @param status  処理の状態
     */
    void markStatusAll(UUID eventId, String status) {
        dsl.update(EVENT_PUBLICATION).set(STATUS, status)
                .where(SERIALIZED_EVENT.contains(eventId.toString())).execute();
    }

    /**
     * イベント ID を含む記録のうち、指定の状態と処理の回数のものの数。
     *
     * @param eventId  イベントを見分ける ID
     * @param status   処理の状態
     * @param attempts 処理の回数
     * @return 記録の数
     */
    int count(UUID eventId, String status, int attempts) {
        return dsl.fetchCount(EVENT_PUBLICATION, SERIALIZED_EVENT.contains(eventId.toString())
                .and(STATUS.eq(status)).and(COMPLETION_ATTEMPTS.eq(attempts)));
    }

    /**
     * 最後の再発行の時刻が指定の時刻の記録の数（ほかのテストの行を含む）。
     *
     * @param resubmittedAt 最後の再発行の時刻
     * @param status        処理の状態（絞らないときは null）
     * @return 記録の数
     */
    int countResubmittedAt(Instant resubmittedAt, String status) {
        var condition = LAST_RESUBMISSION_DATE.eq(resubmittedAt);
        return dsl.fetchCount(EVENT_PUBLICATION, status == null ? condition : condition.and(STATUS.eq(status)));
    }

    /**
     * 記録の行を、リスナーを介さずに 1 行直接入れる（選び方の SQL のテスト用）。
     *
     * <p>イベントの JSON はマーカーの ID だけを持つ。マーカーの ID で {@link #deleteAll(UUID)} が使える。
     * リスナーが受けられる形ではないので、再発行のジョブには使わない。
     *
     * @param marker               記録を見分けるマーカーの ID
     * @param status               処理の状態
     * @param attempts             処理の回数
     * @param publicationDate      発行の時刻
     * @param lastResubmissionDate 最後の再発行の時刻（なければ null）
     * @param completionDate       処理を終えた時刻（未完了なら null）
     * @return 入れた行の ID
     */
    UUID insertRecord(UUID marker, String status, int attempts, Instant publicationDate,
                      Instant lastResubmissionDate, Instant completionDate) {
        var id = UUID.randomUUID();
        dsl.insertInto(EVENT_PUBLICATION)
                .columns(ID, LISTENER_ID, EVENT_TYPE, SERIALIZED_EVENT, PUBLICATION_DATE, COMPLETION_DATE, STATUS,
                        COMPLETION_ATTEMPTS, LAST_RESUBMISSION_DATE)
                .values(id, "test-listener", "test.UnknownEvent", "{\"marker\":\"" + marker + "\"}", publicationDate,
                        completionDate, status, attempts, lastResubmissionDate)
                .execute();
        return id;
    }

    /**
     * 発行したイベントについて、未完了の記録があるか。
     *
     * @param event 発行したイベント
     * @return 未完了の発行の記録があれば true
     */
    boolean isIncomplete(Object event) {
        return registry.findIncompletePublications().stream()
                .anyMatch(publication -> publication.getEvent().equals(event));
    }

    /**
     * イベント発行記録の表の、未完了の行の数（ほかのテストの行を含む）。
     *
     * @return 行の数
     */
    int countIncomplete() {
        return dsl.fetchCount(EVENT_PUBLICATION, COMPLETION_DATE.isNull());
    }

    /**
     * 止めたイベントの表の行の数（ほかのテストの行を含む）。
     *
     * @return 行の数
     */
    int countStopped() {
        return dsl.fetchCount(EVENT_PUBLICATION_STOPPED);
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

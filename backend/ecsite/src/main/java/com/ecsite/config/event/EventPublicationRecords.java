package com.ecsite.config.event;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Spring Modulith のイベントの記録と、止めたイベントの直接の読み書き
 *
 * <p>止めたイベントは、イベント発行記録の表から止めたイベントの表へ移すことで表す。
 */
@Component
@RequiredArgsConstructor
class EventPublicationRecords {

    /**
     * イベント発行記録のテーブル
     *
     * <p>Spring Modulith の表。モジュールの外なので、jOOQ の生成クラスでなく名前で指す。
     */
    private static final Table<?> EVENT_PUBLICATION = DSL.table(DSL.name("common", "event_publication"));

    /**
     * 止めたイベントのテーブル
     *
     * <p>列は、イベント発行記録の表の列のうち復旧に必要なものと同じ名前に、止めた日時を加えたもの。
     * イベント発行記録の表と同じく、名前で指す。
     */
    private static final Table<?> EVENT_PUBLICATION_STOPPED = DSL.table(DSL.name("common", "event_publication_stopped"));

    /**
     * イベントの記録を移す共通テーブル式の名前
     */
    private static final String MOVED = "moved";

    /**
     * イベントの記録の ID
     */
    private static final Field<UUID> ID = DSL.field(DSL.name("id"), UUID.class);

    /**
     * イベントの種類
     */
    private static final Field<String> EVENT_TYPE = DSL.field(DSL.name("event_type"), String.class);

    /**
     * リスナーの ID
     */
    private static final Field<String> LISTENER_ID = DSL.field(DSL.name("listener_id"), String.class);

    /**
     * イベントを JSON にした中身
     */
    private static final Field<String> SERIALIZED_EVENT = DSL.field(DSL.name("serialized_event"), String.class);

    /**
     * 発行した時刻
     */
    private static final Field<Instant> PUBLICATION_DATE = DSL.field(DSL.name("publication_date"), Instant.class);

    /**
     * 処理の状態
     */
    private static final Field<String> STATUS = DSL.field(DSL.name("status"), String.class);

    /**
     * 処理の回数（発行の時の 1 回と再発行の回数）
     */
    private static final Field<Integer> COMPLETION_ATTEMPTS = DSL.field(DSL.name("completion_attempts"), Integer.class);

    /**
     * 最後に再発行した時刻
     */
    private static final Field<Instant> LAST_RESUBMISSION_DATE = DSL.field(DSL.name("last_resubmission_date"), Instant.class);

    /**
     * 処理を終えた時刻
     */
    private static final Field<Instant> COMPLETION_DATE = DSL.field(DSL.name("completion_date"), Instant.class);

    /**
     * 止めた日時
     */
    private static final Field<Instant> STOPPED_DATE = DSL.field(DSL.name("stopped_date"), Instant.class);

    /**
     * 処理を終えた状態
     */
    private static final String COMPLETED = "COMPLETED";

    /**
     * 最後の処理に失敗した状態
     */
    private static final String FAILED = "FAILED";

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

    /**
     * 止めた日時を取る時計
     */
    private final Clock clock;

    /**
     * 処理の回数が上限以上で、最後の処理に失敗した未完了のイベントを、止めたイベントの表に移す。
     *
     * @param maxAttempts 処理の回数の上限
     * @return 移したイベント
     */
    List<StoppedPublication> moveStopped(int maxAttempts) {
        // 1 文で消して入れ、移した行だけを返す。2 台が同時に動いても、同じイベントを 2 回移さず、2 回ログに出さない。
        // 削除した文が行をロックし、同時に同じ行を消そうとした別の文は、ロックが解けた後に行が消えているので、
        // その行を返さないため（PostgreSQL の READ COMMITTED の振る舞い）。
        // 上限を下げたときも止めるので、回数は上限以上を選ぶ。
        // 処理中（RESUBMITTED など）のものは、最後の処理の結果が出ていないので除く
        var moved = DSL.name(MOVED).as(dsl.deleteFrom(EVENT_PUBLICATION)
                .where(STATUS.eq(FAILED))
                .and(COMPLETION_ATTEMPTS.ge(maxAttempts))
                // Spring Modulith は処理を終えた行を失敗にしないが、未完了の行だけを移すことを、条件でも示す
                .and(COMPLETION_DATE.isNull())
                .returning(ID, LISTENER_ID, EVENT_TYPE, SERIALIZED_EVENT, PUBLICATION_DATE,
                        COMPLETION_ATTEMPTS, LAST_RESUBMISSION_DATE));
        return dsl.with(moved)
                .insertInto(EVENT_PUBLICATION_STOPPED, ID, LISTENER_ID, EVENT_TYPE, SERIALIZED_EVENT, PUBLICATION_DATE,
                        COMPLETION_ATTEMPTS, LAST_RESUBMISSION_DATE, STOPPED_DATE)
                .select(DSL.select(ID, LISTENER_ID, EVENT_TYPE, SERIALIZED_EVENT, PUBLICATION_DATE,
                                COMPLETION_ATTEMPTS, LAST_RESUBMISSION_DATE, DSL.val(clock.instant()))
                        .from(moved))
                // 止めたイベントの表に同じ ID の行が残っていると（復旧の手順の SQL を使わずに戻して消し忘れたときなど）、
                // 文全体が主キーの重複で失敗し、ジョブが再発行の前に例外で止まるので、移した行の値と止めた日時で上書きする。
                // 重複した行を捨てる（DO NOTHING）と、イベント発行記録の表から消した行の内容が失われる。上書きした行も返し、ログに出す
                .onConflict(ID)
                .doUpdate()
                .set(LISTENER_ID, DSL.excluded(LISTENER_ID))
                .set(EVENT_TYPE, DSL.excluded(EVENT_TYPE))
                .set(SERIALIZED_EVENT, DSL.excluded(SERIALIZED_EVENT))
                .set(PUBLICATION_DATE, DSL.excluded(PUBLICATION_DATE))
                .set(COMPLETION_ATTEMPTS, DSL.excluded(COMPLETION_ATTEMPTS))
                .set(LAST_RESUBMISSION_DATE, DSL.excluded(LAST_RESUBMISSION_DATE))
                .set(STOPPED_DATE, DSL.excluded(STOPPED_DATE))
                .returning(stopped(ID), stopped(EVENT_TYPE), stopped(LISTENER_ID), stopped(COMPLETION_ATTEMPTS))
                .fetch(row -> new StoppedPublication(row.get(stopped(ID)), row.get(stopped(EVENT_TYPE)),
                        row.get(stopped(LISTENER_ID)), row.get(stopped(COMPLETION_ATTEMPTS))));
    }

    /**
     * イベント発行記録の表の未完了のイベント（止めたイベントを含まない）と、止めたイベントを数える。
     *
     * <p>2 つの件数を 1 つの文で数えるので、同じ時点の値になる。
     *
     * @return 2 つの件数
     */
    PublicationCounts countIncompleteAndStopped() {
        var incompleteNotStopped = DSL.field(DSL.selectCount().from(EVENT_PUBLICATION).where(COMPLETION_DATE.isNull()));
        var stopped = DSL.field(DSL.selectCount().from(EVENT_PUBLICATION_STOPPED));
        return dsl.select(incompleteNotStopped, stopped)
                .fetchSingle(row -> new PublicationCounts(row.value1(), row.value2()));
    }

    /**
     * 処理を終えた時刻が基準の時刻より前のイベントを消す。
     *
     * <p>未完了のイベントと止めたイベントは消さない。
     *
     * @param threshold 基準の時刻（これより前に処理を終えたものを消す）
     * @return 消した件数
     */
    int deleteCompletedBefore(Instant threshold) {
        // Spring Modulith の「処理を終えた時刻で消す」操作と同じ条件。消した件数をログに出すため、自分で消す
        return dsl.deleteFrom(EVENT_PUBLICATION)
                .where(COMPLETION_DATE.lt(threshold))
                .and(STATUS.eq(COMPLETED).or(STATUS.isNull()))
                .execute();
    }

    /**
     * 止めたイベントのテーブルで修飾した列を返す。
     *
     * @param field 列
     * @param <T>   列の型
     * @return 修飾した列
     */
    private static <T> Field<T> stopped(Field<T> field) {
        return DSL.field(EVENT_PUBLICATION_STOPPED.getQualifiedName().append(field.getUnqualifiedName()), field.getType());
    }

    /**
     * 止めたイベント
     *
     * <p>イベントの中身は持たない。
     *
     * @param id                 イベントの記録の ID
     * @param eventType          イベントの種類（クラスの完全修飾名）
     * @param listenerId         リスナーの ID
     * @param completionAttempts 処理の回数
     */
    record StoppedPublication(UUID id, String eventType, String listenerId, int completionAttempts) {
    }

    /**
     * 未完了のイベントと止めたイベントの件数
     *
     * @param incompleteNotStopped イベント発行記録の表の未完了のイベント（止めたイベントを含まない）の件数
     * @param stopped              止めたイベントの件数
     */
    record PublicationCounts(int incompleteNotStopped, int stopped) {
    }
}

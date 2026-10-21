package com.ecsite.config;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Spring Modulith のイベントの記録を、再発行のジョブのために直接読み書きする（バックエンドのアーキテクチャ 第 5 節）。<br />
 * 止めた印は、処理の回数を上限より 1 つ多くした値で表す（Spring Modulith の再発行は、処理の回数を見ずに読むため、
 * 絞り込みは再発行のジョブが行う）。
 */
@Component
@RequiredArgsConstructor
class EventPublicationRecords {

    /**
     * イベントの記録のテーブル（Spring Modulith の表。モジュールの外なので、jOOQ の生成クラスでなく名前で指す）
     */
    private static final Table<?> EVENT_PUBLICATION = DSL.table(DSL.name("common", "event_publication"));

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
     * 処理の状態
     */
    private static final Field<String> STATUS = DSL.field(DSL.name("status"), String.class);

    /**
     * 処理の回数（発行の時の 1 回と再発行の回数）
     */
    private static final Field<Integer> COMPLETION_ATTEMPTS = DSL.field(DSL.name("completion_attempts"), Integer.class);

    /**
     * 処理を終えた時刻
     */
    private static final Field<Instant> COMPLETION_DATE = DSL.field(DSL.name("completion_date"), Instant.class);

    /**
     * 最後の処理に失敗した状態
     */
    private static final String FAILED = "FAILED";

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

    /**
     * 処理の回数が上限に達し、最後の処理に失敗したイベントに、止めた印を付ける。
     *
     * @param maxAttempts 処理の回数の上限
     * @return 印を付けたイベント
     */
    List<StoppedPublication> markStopped(int maxAttempts) {
        // 1 文で印を付け、付けた行だけを返す（同じイベントを 2 回ログに出さない）。
        // 処理中（RESUBMITTED など）のものは、最後の処理の結果が出ていないので除く
        return dsl.update(EVENT_PUBLICATION)
                .set(COMPLETION_ATTEMPTS, COMPLETION_ATTEMPTS.plus(1))
                .where(STATUS.eq(FAILED))
                .and(COMPLETION_ATTEMPTS.eq(maxAttempts))
                .and(COMPLETION_DATE.isNull())
                .returning(ID, EVENT_TYPE, LISTENER_ID)
                .fetch(row -> new StoppedPublication(row.get(ID), row.get(EVENT_TYPE), row.get(LISTENER_ID), maxAttempts));
    }

    /**
     * 未完了のイベントの件数（止めたイベントを含む）
     *
     * @return 件数
     */
    int countIncomplete() {
        return dsl.fetchCount(EVENT_PUBLICATION, COMPLETION_DATE.isNull());
    }

    /**
     * 止めたイベントの件数
     *
     * @param maxAttempts 処理の回数の上限
     * @return 件数
     */
    int countStopped(int maxAttempts) {
        return dsl.fetchCount(EVENT_PUBLICATION, COMPLETION_DATE.isNull().and(COMPLETION_ATTEMPTS.gt(maxAttempts)));
    }

    /**
     * 止めたイベント（ログに出す項目だけ。イベントの中身は持たない）
     *
     * @param id                 イベントの記録の ID
     * @param eventType          イベントの種類（クラスの完全修飾名）
     * @param listenerId         リスナーの ID
     * @param completionAttempts 処理の回数
     */
    record StoppedPublication(UUID id, String eventType, String listenerId, int completionAttempts) {
    }
}

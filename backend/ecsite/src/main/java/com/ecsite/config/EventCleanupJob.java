package com.ecsite.config;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * 処理の済んだイベントの片付けのジョブ（バックエンドのアーキテクチャ 第 5 節）。<br />
 * 処理を終えた時刻から保存期間を過ぎたイベントの記録を消す。未完了のイベントと止めたイベントは消さない。
 * 処理の済んだイベントは他の操作が読み書きしないので、件数の上限を置かずに 1 文で消す。
 * 間隔と保存期間は片付けの設定 {@code ecsite.scheduling.event-cleanup}（既定 1 時間、7 日）で変えられる。
 */
@Slf4j
@Component
@RequiredArgsConstructor
class EventCleanupJob {

    /**
     * イベントの記録のテーブル（Spring Modulith の表。モジュールの外なので、jOOQ の生成クラスでなく名前で指す）
     */
    private static final Table<?> EVENT_PUBLICATION = DSL.table(DSL.name("common", "event_publication"));

    /**
     * 処理を終えた時刻
     */
    private static final Field<Instant> COMPLETION_DATE = DSL.field(DSL.name("completion_date"), Instant.class);

    /**
     * 処理の状態
     */
    private static final Field<String> STATUS = DSL.field(DSL.name("status"), String.class);

    /**
     * 処理を終えた状態
     */
    private static final String COMPLETED = "COMPLETED";

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

    /**
     * 片付けの設定
     */
    private final Properties properties;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * 処理を終えた時刻から保存期間を過ぎたイベントを消す。
     */
    // 片付けの設定の Bean の名前は「接頭辞-完全修飾名」（@EnableConfigurationProperties の決まり）
    @Scheduled(fixedDelayString = "#{@'ecsite.scheduling.event-cleanup-"
            + "com.ecsite.config.EventCleanupJob$Properties'.interval()}")
    @SchedulerLock(name = "event-cleanup")
    @Transactional
    void cleanUp() {
        // Spring Modulith の「処理を終えた時刻で消す」操作と同じ条件。消した件数をログに出すため、自分で消す
        int deleted = dsl.deleteFrom(EVENT_PUBLICATION)
                .where(COMPLETION_DATE.lt(clock.instant().minus(properties.retention())))
                .and(STATUS.eq(COMPLETED).or(STATUS.isNull()))
                .execute();
        if (deleted > 0) {
            log.info("処理の済んだイベントを消した：{} 件", deleted);
        } else {
            log.debug("処理の済んだイベントを消した：0 件");
        }
    }

    /**
     * 片付けの設定。環境変数で上書きする（例：ECSITE_SCHEDULING_EVENT_CLEANUP_RETENTION）。
     *
     * @param interval  片付けのジョブの間隔
     * @param retention 保存期間（処理を終えた時刻から、消すまでの時間）
     */
    @Validated
    @ConfigurationProperties("ecsite.scheduling.event-cleanup")
    record Properties(@DefaultValue("1h") @NotNull Duration interval, @DefaultValue("7d") @NotNull Duration retention) {
    }
}

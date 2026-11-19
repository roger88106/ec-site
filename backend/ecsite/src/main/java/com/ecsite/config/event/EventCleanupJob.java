package com.ecsite.config.event;

import com.ecsite.common.scheduling.ExpiredDataCleanup;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Clock;
import java.time.Duration;

/**
 * 処理の済んだイベントの片付けのジョブ
 */
@Slf4j
@Component
@RequiredArgsConstructor
class EventCleanupJob {

    /**
     * 1 回の削除で消す件数の上限
     *
     * <p>文の実行のタイムアウトに収まる件数にする。環境で変えない値なので、設定にしない（他の片付けの 1 回の件数の既定と同じ）。
     */
    private static final int BATCH_SIZE = 1000;

    /**
     * イベントの記録
     */
    private final EventPublicationRecords publicationRecords;

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
     *
     * <p>未完了のイベントと止めたイベントは消さない。
     */
    // 片付けの設定の Bean の名前は「接頭辞-完全修飾名」（設定の型の登録の決まり）
    @Scheduled(fixedDelayString = "#{@'ecsite.scheduling.event-cleanup-"
            + "com.ecsite.config.event.EventCleanupJob$Properties'.interval()}")
    // ロックを持つ時間の上限は 10 分。DB の削除だけで外部を呼ばず、通常は上限よりずっと短く終わる。止まったインスタンスのロックが長く残らない値にする
    @SchedulerLock(name = "event-cleanup", lockAtMostFor = "PT10M")
    void cleanUp() {
        // 件数の上限ずつ、残りがなくなるまで消す。1 文で消すと、溜まった行が多いとき（止まっていた後、保存期間を短くした後）に、
        // 文の実行のタイムアウト（ecsite.db.statement-timeout）で取り消され続け、片付けが止まる
        var completedBefore = clock.instant().minus(properties.retention());
        ExpiredDataCleanup.run(log, "処理の済んだイベント", BATCH_SIZE,
                () -> publicationRecords.deleteCompletedBefore(completedBefore, BATCH_SIZE));
    }

    /**
     * 片付けの設定
     *
     * <p>環境変数で上書きする（例：ECSITE_SCHEDULING_EVENT_CLEANUP_RETENTION）。
     *
     * @param interval  片付けのジョブの間隔
     * @param retention 保存期間（処理を終えた時刻から、消すまでの時間）
     */
    @Validated
    @ConfigurationProperties("ecsite.scheduling.event-cleanup")
    record Properties(
            @DefaultValue("1h") @NotNull @DurationMin(millis = 1) Duration interval,
            @DefaultValue("7d") @NotNull @DurationMin(nanos = 0) Duration retention) {
    }
}

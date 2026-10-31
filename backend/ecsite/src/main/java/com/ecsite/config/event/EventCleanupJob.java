package com.ecsite.config.event;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Clock;
import java.time.Duration;

/**
 * 処理の済んだイベントの片付けのジョブ（バックエンドのアーキテクチャの「水平スケール」）。<br />
 * 処理を終えた時刻から保存期間を過ぎたイベントの記録を消す。未完了のイベントと止めたイベントは消さない。
 * 処理の済んだイベントは他の操作が読み書きしないので、件数の上限を置かずに 1 文で消す。
 * 間隔と保存期間は片付けの設定 {@code ecsite.scheduling.event-cleanup}（既定 1 時間、7 日）で変えられる。
 */
@Slf4j
@Component
@RequiredArgsConstructor
class EventCleanupJob {

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
     */
    // 片付けの設定の Bean の名前は「接頭辞-完全修飾名」（@EnableConfigurationProperties の決まり）
    @Scheduled(fixedDelayString = "#{@'ecsite.scheduling.event-cleanup-"
            + "com.ecsite.config.event.EventCleanupJob$Properties'.interval()}")
    @SchedulerLock(name = "event-cleanup")
    void cleanUp() {
        int deleted = publicationRecords.deleteCompletedBefore(clock.instant().minus(properties.retention()));
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

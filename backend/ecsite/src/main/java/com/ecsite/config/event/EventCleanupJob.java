package com.ecsite.config.event;

import com.ecsite.common.scheduling.ExpiredDataCleanup;
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
 * 処理の済んだイベントの片付けのジョブ
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
     *
     * <p>未完了のイベントと止めたイベントは消さない。
     */
    // 片付けの設定の Bean の名前は「接頭辞-完全修飾名」（@EnableConfigurationProperties の決まり）
    @Scheduled(fixedDelayString = "#{@'ecsite.scheduling.event-cleanup-"
            + "com.ecsite.config.event.EventCleanupJob$Properties'.interval()}")
    @SchedulerLock(name = "event-cleanup")
    void cleanUp() {
        // 処理の済んだイベントは他の操作が読み書きしないので、件数の上限を置かずに 1 文で消す
        int deleted = publicationRecords.deleteCompletedBefore(clock.instant().minus(properties.retention()));
        ExpiredDataCleanup.logDeleted(log, "処理の済んだイベント", deleted);
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
    record Properties(@DefaultValue("1h") @NotNull Duration interval, @DefaultValue("7d") @NotNull Duration retention) {
    }
}

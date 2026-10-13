package com.ecsite.config;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 未完了のイベントを再発行する定期ジョブ（バックエンドのアーキテクチャ 第 5 節）。<br />
 * リスナーの処理に失敗した（または処理の途中でインスタンスが止まった）イベントを、もう一度リスナーに渡す。
 * リスナーは冪等に作るので、重ねて届いてもよい。
 * 間隔は {@code ecsite.scheduling.event-resubmission.interval}（既定 1 分）で変えられる。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(EventResubmissionJob.Properties.class)
class EventResubmissionJob {

    /**
     * 未完了のイベント
     */
    private final IncompleteEventPublications incompletePublications;

    /**
     * 再発行の設定
     */
    private final Properties properties;

    /**
     * 発行から一定時間（既定 5 分）より前の、未完了のイベントを再発行する。<br />
     * 発行の直後のイベントは、まだ処理中のことがあるので除く。
     */
    @Scheduled(fixedDelayString = "${ecsite.scheduling.event-resubmission.interval:PT1M}")
    @SchedulerLock(name = "event-resubmission")
    void resubmit() {
        log.debug("未完了のイベントを再発行する（発行から {} より前のもの）", properties.minAge());
        incompletePublications.resubmitIncompletePublicationsOlderThan(properties.minAge());
    }

    /**
     * 再発行の設定。環境変数で上書きする（例：ECSITE_SCHEDULING_EVENT_RESUBMISSION_MIN_AGE）。
     *
     * @param minAge 再発行するイベントの、発行からの時間の下限
     */
    @Validated
    @ConfigurationProperties("ecsite.scheduling.event-resubmission")
    record Properties(@DefaultValue("5m") @NotNull Duration minAge) {
    }
}

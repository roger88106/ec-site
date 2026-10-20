package com.ecsite.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Clock;
import java.time.Duration;

/**
 * 未完了のイベントを再発行する定期ジョブ（バックエンドのアーキテクチャ 第 5 節）。<br />
 * リスナーの処理に失敗した（または処理の途中でインスタンスが止まった）イベントを、もう一度リスナーに渡す。
 * リスナーは冪等に作るので、重ねて届いてもよい。
 * 間隔は再発行の設定 {@code ecsite.scheduling.event-resubmission.interval}（既定 1 分）で変えられる。
 */
@Slf4j
@Component
@RequiredArgsConstructor
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
     * 時計
     */
    private final Clock clock;

    /**
     * イベントの記録
     */
    private final EventPublicationRecords publicationRecords;

    /**
     * 発行から一定時間（既定 5 分）より前の、未完了のイベントを再発行する。<br />
     * 発行の直後のイベントは、まだ処理中のことがあるので除く。
     * 処理の回数（発行の時の 1 回と再発行の回数）が上限（既定 60）に達したイベントは、再発行しない。
     */
    // 再発行の設定の Bean の名前は「接頭辞-完全修飾名」（@EnableConfigurationProperties の決まり）
    @Scheduled(fixedDelayString = "#{@'ecsite.scheduling.event-resubmission-"
            + "com.ecsite.config.EventResubmissionJob$Properties'.interval()}")
    @SchedulerLock(name = "event-resubmission")
    void resubmit() {
        for (var stopped : publicationRecords.markStopped(properties.maxAttempts())) {
            log.error("処理の回数が上限に達したイベントの再発行を止めた：種類 {}、イベント ID {}、リスナー {}、処理の回数 {}",
                    stopped.eventType(), stopped.id(), stopped.listenerId(), stopped.completionAttempts());
        }
        log.debug("未完了のイベントを再発行する（発行から {} より前で、処理の回数が {} より少ないもの）",
                properties.minAge(), properties.maxAttempts());
        var publishedBefore = clock.instant().minus(properties.minAge());
        // 再発行の選択肢（ResubmissionOptions）は使わない：状態が FAILED のものだけを、件数の上限で古い順に読んでから絞るので、
        // 止めたイベントが上限の件数だけ溜まると、新しいイベントが再発行されなくなる。処理中のまま止まったイベントも拾わない
        incompletePublications.resubmitIncompletePublications(publication ->
                publication.getPublicationDate().isBefore(publishedBefore)
                        && publication.getCompletionAttempts() < properties.maxAttempts());
    }

    /**
     * 再発行の設定。環境変数で上書きする（例：ECSITE_SCHEDULING_EVENT_RESUBMISSION_MIN_AGE）。
     *
     * @param interval    再発行のジョブの間隔
     * @param minAge      再発行するイベントの、発行からの時間の下限
     * @param maxAttempts 処理の回数（発行の時の 1 回と再発行の回数）の上限
     */
    @Validated
    @ConfigurationProperties("ecsite.scheduling.event-resubmission")
    record Properties(@DefaultValue("1m") @NotNull Duration interval, @DefaultValue("5m") @NotNull Duration minAge,
                      @DefaultValue("60") @Positive int maxAttempts) {
    }
}

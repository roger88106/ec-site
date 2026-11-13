package com.ecsite.config.event;

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
 * 未完了のイベントの再発行のジョブ
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
     * 未完了と止めたイベントの件数のメトリクス
     */
    private final EventPublicationMetrics publicationMetrics;

    /**
     * 発行から一定時間より前の、未完了のイベントを再発行する。
     *
     * <p>リスナーの処理に失敗した、または処理の途中でインスタンスが止まったイベントを、もう一度リスナーに渡す。
     * 次のイベントは再発行しない。
     * <ul>
     *     <li>最後の試行から、再発行の間隔がたっていない</li>
     *     <li>処理の回数（発行の時の 1 回と再発行の回数）が上限に達した。最後の処理に失敗していれば、止めた印を付けてエラーのログに出す</li>
     * </ul>
     * <p>終わりに、未完了と止めたイベントの件数をメトリクスに記録する。時間と回数は {@link Properties}。
     */
    // 再発行の設定の Bean の名前は「接頭辞-完全修飾名」（@EnableConfigurationProperties の決まり）
    @Scheduled(fixedDelayString = "#{@'ecsite.scheduling.event-resubmission-"
            + "com.ecsite.config.event.EventResubmissionJob$Properties'.interval()}")
    @SchedulerLock(name = "event-resubmission")
    void resubmit() {
        for (var stopped : publicationRecords.markStopped(properties.maxAttempts())) {
            log.error("処理の回数が上限に達したイベントの再発行を止めた：種類 {}、イベント ID {}、リスナー {}、処理の回数 {}",
                    stopped.eventType(), stopped.id(), stopped.listenerId(), stopped.completionAttempts());
        }
        log.debug("未完了のイベントを再発行する（発行から {} より前で、最後の試行から {} 以上たち、処理の回数が {} より少ないもの）",
                properties.minAge(), properties.interval(), properties.maxAttempts());
        var now = clock.instant();
        var publishedBefore = now.minus(properties.minAge());
        var lastTriedBy = now.minus(properties.interval());
        // 選ぶ条件：
        // - 発行から下限の時間がたった（発行の直後は、まだ処理中のことがある）
        // - 最後の試行から間隔がたった（間隔のタイマーはインスタンスごとにあるので、台数のぶんだけ回数が速く増えないように）
        // - 回数が上限より少ない
        // 再発行の選択肢（ResubmissionOptions）は使わない。FAILED のものを件数の上限まで古い順に読んでから絞るので、
        // 止めたイベントが溜まると、新しいイベントが再発行されなくなるため。
        // 試行の途中でインスタンスが止まったイベントは、Spring Modulith の監視（spring.modulith.events.staleness）が
        // 失敗の状態に変えた後の回で、再発行するか止める
        incompletePublications.resubmitIncompletePublications(publication ->
                publication.getPublicationDate().isBefore(publishedBefore)
                        && !publication.getLastPublicationDate().isAfter(lastTriedBy)
                        && publication.getCompletionAttempts() < properties.maxAttempts());
        publicationMetrics.record(publicationRecords.countIncomplete(),
                publicationRecords.countStopped(properties.maxAttempts()));
    }

    /**
     * 再発行の設定
     *
     * <p>環境変数で上書きする（例：ECSITE_SCHEDULING_EVENT_RESUBMISSION_MIN_AGE）。
     *
     * @param interval    再発行のジョブの間隔。同じイベントの試行の間も、この間隔を空ける
     * @param minAge      再発行するイベントの、発行からの時間の下限
     * @param maxAttempts 処理の回数（発行の時の 1 回と再発行の回数）の上限
     */
    @Validated
    @ConfigurationProperties("ecsite.scheduling.event-resubmission")
    record Properties(@DefaultValue("1m") @NotNull Duration interval, @DefaultValue("5m") @NotNull Duration minAge,
                      @DefaultValue("60") @Positive int maxAttempts) {
    }
}

package com.ecsite.config.event;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Clock;
import java.time.Duration;

/**
 * 最後の処理に失敗した、未完了のイベントの再発行のジョブ
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
     * 発行から一定時間より前の、最後の処理に失敗した未完了のイベントを再発行する。
     *
     * <p>最後の処理に失敗したイベントを、もう一度リスナーに渡す。処理の途中でインスタンスが止まったイベントは、
     * Spring Modulith の監視が失敗の状態にした後の回で渡す。
     * 次のイベントは再発行しない。
     * <ul>
     *     <li>最後の処理の結果が出ていない（処理中など）</li>
     *     <li>最後の試行から、再発行の間隔がたっていない</li>
     *     <li>処理の回数（発行の時の 1 回と再発行の回数）が上限に達した。最後の処理に失敗していれば、止めたイベントの表に移してエラーのログに出す</li>
     * </ul>
     * <p>終わりに、未完了と止めたイベントの件数をメトリクスに記録する。時間と回数は {@link Properties}。
     */
    // 再発行の設定の Bean の名前は「接頭辞-完全修飾名」（設定の型の登録の決まり）
    @Scheduled(fixedDelayString = "#{@'ecsite.scheduling.event-resubmission-"
            + "com.ecsite.config.event.EventResubmissionJob$Properties'.interval()}")
    // ロックを持つ時間の上限は 5 分。実行の間隔が 1 分なので、止まったインスタンスのロックが長く残らないようにする
    @SchedulerLock(name = "event-resubmission", lockAtMostFor = "PT5M")
    void resubmit() {
        for (var stopped : publicationRecords.moveStopped(properties.maxAttempts())) {
            log.error("処理の回数が上限に達したイベントの再発行を止めた：種類 {}、イベント ID {}、リスナー {}、処理の回数 {}",
                    stopped.eventType(), stopped.id(), stopped.listenerId(), stopped.completionAttempts());
        }
        log.debug("未完了のイベントを再発行する（最後の処理に失敗し、発行から {} より前で、最後の試行から {} 以上たち、処理の回数が {} より少ないもの）",
                properties.minAge(), properties.interval(), properties.maxAttempts());
        var lastTriedBy = clock.instant().minus(properties.interval());
        // 選ぶ条件：
        // - 最後の処理に失敗した。絞り込みの条件には入れない。失敗したイベントを再発行する Spring Modulith の操作は、
        //   失敗の状態の行と、状態が空で未完了の行を読むが、今の表の作り方では状態が必ず入るので、ジョブが読むのは失敗の行だけになる。
        //   処理中のものは、リスナーの処理が続いていることがあるので読まない。処理の途中でインスタンスが止まったものは、
        //   Spring Modulith の監視（spring.modulith.events.staleness）が失敗の状態にした後の回で読む
        // - 発行から下限の時間がたった（発行の直後は、まだ処理中のことがある）
        // - 最後の試行から間隔がたった（間隔のタイマーはインスタンスごとにあるので、台数のぶんだけ回数が速く増えないように）
        // - 回数が上限より少ない
        // 読む件数の上限は付けない（件数の上限を最大の値にする）。この操作は失敗の状態の行を発行の古い順に件数の上限まで
        // 読んでから絞るので、上限を付けると、古い失敗したイベントだけが毎回読まれ、新しいイベントの再発行が遅れるため。
        // 止めたイベントは別の表に移るので、失敗の状態の行は、まだ再発行するものだけになる
        incompletePublications.resubmitIncompletePublications(ResubmissionOptions.defaults()
                .withMinAge(properties.minAge())
                .withBatchSize(Integer.MAX_VALUE)
                .withFilter(publication -> !publication.getLastPublicationDate().isAfter(lastTriedBy)
                        && publication.getCompletionAttempts() < properties.maxAttempts()));
        // 2 つの件数を 1 つの文で数え、同じ時点の値にする（文を分けると、2 つの文の間に復旧で行が戻ったとき、
        // その行が両方の件数に入り、未完了の件数が多くなる）
        var counts = publicationRecords.countIncompleteAndStopped();
        publicationMetrics.record(counts.incompleteNotStopped() + counts.stopped(), counts.stopped());
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
    record Properties(
            @DefaultValue("1m") @NotNull @DurationMin(millis = 1) Duration interval,
            @DefaultValue("5m") @NotNull @DurationMin(millis = 1) Duration minAge,
            @DefaultValue("60") @Positive int maxAttempts) {
    }
}

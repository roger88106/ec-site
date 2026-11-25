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
import java.util.HashSet;

/**
 * 最後の処理に失敗した、未完了のイベントの再発行のジョブ
 */
@Slf4j
@Component
@RequiredArgsConstructor
class EventResubmissionJob {

    /**
     * 再発行中（RESUBMITTED）の件数と、1 回に再発行する件数を合わせた上限
     *
     * <p>リスナーのスレッドプールのキューの上限（既定は 1000。{@code spring.task.execution.pool.queue-capacity}、
     * {@code ecsite.async.external-service.queue-capacity}）より小さくする。環境で変えない値なので、設定にしない。
     */
    private static final int BATCH_SIZE = 500;

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
     * 最後の試行の古いものから、再発行中の件数と合わせて {@link #BATCH_SIZE} 件まで再発行する。
     *
     * <p>次のイベントは再発行しない。
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
        resubmitTargets();
        // 2 つの件数を 1 つの文で数え、同じ時点の値にする（文を分けると、2 つの文の間に復旧で行が戻ったとき、
        // その行が両方の件数に入り、未完了の件数が多くなる）
        var counts = publicationRecords.countIncompleteAndStopped();
        publicationMetrics.record(counts.incompleteNotStopped() + counts.stopped(), counts.stopped());
    }

    /**
     * 再発行するイベントを選び、Spring Modulith に再発行させる。
     */
    private void resubmitTargets() {
        // 件数に上限を置く。リスナーのスレッドプールのキューの上限（1000）を超えて渡すと、キューが拒否し、
        // そのイベントは処理されないまま、処理の回数だけが増える（Spring Modulith は、再発行の記録（回数を 1 増やす）を
        // 先に書いてから、リスナーを呼ぶため）。上限は再発行中（RESUBMITTED）の件数と合わせ、
        // 残りの枠がなければ、その回は再発行せず、次の回に回す
        var resubmitted = publicationRecords.countResubmitted();
        var remaining = BATCH_SIZE - resubmitted;
        if (remaining <= 0) {
            log.info("再発行中の件数が上限に達しているので、再発行しない：上限 {}、再発行中 {}", BATCH_SIZE, resubmitted);
            return;
        }
        // 再発行するイベントは自前で選ぶ。Spring Modulith は失敗の行を発行の古い順に件数まで読んでから filter を掛けるので、
        // 失敗し続ける古い行が上限以上あると、新しい行が読まれない。そこで最後の試行の古い順に選ぶ。条件：
        // - 最後の処理に失敗した。処理中のものは、リスナーの処理が続いていることがあるので選ばない。
        //   処理の途中でインスタンスが止まったものは、Spring Modulith の監視（spring.modulith.events.staleness）が
        //   失敗の状態にした後の回で選ぶ
        // - 発行から下限の時間がたった（発行の直後は、まだ処理中のことがある）
        // - 最後の試行から間隔がたった（間隔のタイマーはインスタンスごとにあるので、台数のぶんだけ回数が速く増えないように）
        // - 回数が上限より少ない
        var now = clock.instant();
        var targets = new HashSet<>(publicationRecords.findResubmissionTargets(
                now.minus(properties.minAge()), now.minus(properties.interval()), properties.maxAttempts(), remaining));
        if (targets.isEmpty()) {
            log.debug("再発行するイベントがない");
            return;
        }
        log.debug("未完了のイベントを {} 件再発行する", targets.size());
        // 件数は自前で絞ったので、Spring Modulith には上限なしで渡し、選んだ ID だけを通す。
        // 上限を渡すと、Spring Modulith が発行の古い順に上限まで読むので、選んだイベントに届かない。
        // そのため、失敗の状態の行を毎回全て読ませる（イベントの本文を復元するのは、filter を通したイベントだけ）。
        // 回数の上限に達した行は、止めたイベントの表に移る
        incompletePublications.resubmitIncompletePublications(ResubmissionOptions.defaults()
                .withMinAge(properties.minAge())
                .withBatchSize(Integer.MAX_VALUE)
                .withMaxInFlight(Integer.MAX_VALUE)
                .withFilter(publication -> targets.contains(publication.getIdentifier())));
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

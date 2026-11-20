package com.ecsite.module.media.application.job;

import com.ecsite.common.scheduling.ExpiredDataCleanup;
import com.ecsite.module.media.application.MediaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * 仮のアセットの片付けのジョブ
 *
 * <p>用意した時刻から保存期間を過ぎた仮のアセットを、1 回の実行で件数の上限まで削除し、配信のファイルはコミットの後に削除する。
 * 間隔と保存期間は仮のアセットの片付けの設定 {@code ecsite.media.cleanup} で変えられる。
 */
@Slf4j
@Component
@RequiredArgsConstructor
class MediaCleanupJob {

    /**
     * メディアのサービス
     */
    private final MediaService service;

    /**
     * 仮のアセットの片付けの設定
     */
    private final MediaCleanupProperties properties;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * 用意した時刻から保存期間を過ぎた仮のアセットを削除する。
     *
     * <p>1 回の実行で、件数の上限までを 1 つのトランザクションで削除する。残りは次の回に削除する。
     */
    // 設定の Bean の名前は「接頭辞-完全修飾名」（設定の型の登録の決まり）
    @Scheduled(fixedDelayString = "#{@'ecsite.media.cleanup-"
            + "com.ecsite.module.media.application.job.MediaCleanupProperties'.interval()}")
    // ロックを持つ時間の上限は 10 分。DB の削除とイベントの記録だけで外部を呼ばず、通常は上限よりずっと短く終わる。止まったインスタンスのロックが長く残らない値にする
    @SchedulerLock(name = "media-cleanup", lockAtMostFor = "PT10M")
    void cleanUp() {
        var preparedBefore = clock.instant().minus(properties.retention());
        // 上限に達しても同じ回で繰り返さない。削除した件数だけ外部サービスを呼ぶリスナーのキューにイベントが入り、
        // キューの上限を超えると他のリスナーのイベントも拒否されるため（イベントの再発行の件数の上限と同じ考え方）
        var deleted = service.deleteExpiredPendingAssets(preparedBefore, properties.batchSize());
        ExpiredDataCleanup.logDeleted(log, "保存期間を過ぎた仮のアセット", deleted);
    }
}

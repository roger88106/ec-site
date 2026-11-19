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
 * <p>用意した時刻から保存期間を過ぎた仮のアセットを消し、配信のファイルはコミットの後に消す。
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
     * 用意した時刻から保存期間を過ぎた仮のアセットを消す。
     *
     * <p>件数の上限ずつ、残りがなくなるまで消す。1 回ごとに別のトランザクションにする。
     */
    // 設定の Bean の名前は「接頭辞-完全修飾名」（設定の型の登録の決まり）
    @Scheduled(fixedDelayString = "#{@'ecsite.media.cleanup-"
            + "com.ecsite.module.media.application.job.MediaCleanupProperties'.interval()}")
    // ロックを持つ時間の上限は 10 分。DB の削除とイベントの記録だけで外部を呼ばず、通常は上限よりずっと短く終わる。止まったインスタンスのロックが長く残らない値にする
    @SchedulerLock(name = "media-cleanup", lockAtMostFor = "PT10M")
    void cleanUp() {
        var preparedBefore = clock.instant().minus(properties.retention());
        ExpiredDataCleanup.run(log, "保存期間を過ぎた仮のアセット", properties.batchSize(),
                () -> service.deleteExpiredPendingAssets(preparedBefore, properties.batchSize()));
    }
}

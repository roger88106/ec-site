package com.ecsite.module.order.application.job;

import com.ecsite.common.scheduling.ExpiredDataCleanup;
import com.ecsite.module.order.application.checkout.CheckoutCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * 注文手続きの片付けのジョブ
 *
 * <p>期限から保存期間を過ぎた注文手続きを、件数の上限ずつ、残りがなくなるまで消す（1 回ごとに別のトランザクション）。
 * 注文になった注文手続きも消す。
 * 間隔と保存期間は注文手続きの片付けの設定 {@code ecsite.order.cleanup} で変えられる。
 */
@Slf4j
@Component
@RequiredArgsConstructor
class CheckoutCleanupJob {

    /**
     * 注文手続きのサービス
     */
    private final CheckoutCommandService service;

    /**
     * 注文手続きの片付けの設定
     */
    private final CheckoutCleanupProperties properties;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * 期限から保存期間を過ぎた注文手続きを消す。
     */
    // 設定の Bean の名前は「接頭辞-完全修飾名」（設定の型の登録の決まり）
    @Scheduled(fixedDelayString = "#{@'ecsite.order.cleanup-"
            + "com.ecsite.module.order.application.job.CheckoutCleanupProperties'.interval()}")
    // ロックを持つ時間の上限は 10 分。DB の削除だけで外部を呼ばず、通常は上限よりずっと短く終わる。止まったインスタンスのロックが長く残らない値にする
    @SchedulerLock(name = "checkout-cleanup", lockAtMostFor = "PT10M")
    void cleanUp() {
        var expiredBefore = clock.instant().minus(properties.retention());
        ExpiredDataCleanup.run(log, "期限切れの注文手続き", properties.batchSize(),
                () -> service.deleteExpired(expiredBefore, properties.batchSize()));
    }
}

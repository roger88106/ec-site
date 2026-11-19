package com.ecsite.module.cart.application.job;

import com.ecsite.common.scheduling.ExpiredDataCleanup;
import com.ecsite.module.cart.application.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * カートの片付けのジョブ
 *
 * <p>期限から保存期間を過ぎたカートを、品目と共に消す。間隔と保存期間はカートの片付けの設定 {@code ecsite.cart.cleanup} で変えられる。
 */
@Slf4j
@Component
@RequiredArgsConstructor
class CartCleanupJob {

    /**
     * カートのサービス
     */
    private final CartService service;

    /**
     * カートの片付けの設定
     */
    private final CartCleanupProperties properties;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * 期限から保存期間を過ぎたカートを消す。
     *
     * <p>件数の上限ずつ、残りがなくなるまで消す。1 回ごとに別のトランザクションにする。
     */
    // 設定の Bean の名前は「接頭辞-完全修飾名」（設定の型の登録の決まり）
    @Scheduled(fixedDelayString = "#{@'ecsite.cart.cleanup-"
            + "com.ecsite.module.cart.application.job.CartCleanupProperties'.interval()}")
    // ロックを持つ時間の上限は 10 分。DB の削除だけで外部を呼ばず、通常は上限よりずっと短く終わる。止まったインスタンスのロックが長く残らない値にする
    @SchedulerLock(name = "cart-cleanup", lockAtMostFor = "PT10M")
    void cleanUp() {
        var expiredBefore = clock.instant().minus(properties.retention());
        ExpiredDataCleanup.run(log, "期限切れのカート", properties.batchSize(),
                () -> service.deleteExpired(expiredBefore, properties.batchSize()));
    }
}

package com.ecsite.module.cart.application;

import com.ecsite.common.scheduling.ExpiredDataCleanup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * カートの片付けのジョブ（docs/03-機能仕様/cart.md「期限」）。<br />
 * 期限から保存期間を過ぎたカートを、品目と共に、件数の上限ずつ、残りがなくなるまで消す（1 回ごとに別のトランザクション）。
 * 間隔と保存期間はカートの片付けの設定 {@code ecsite.cart.cleanup}（既定 1 時間、0）で変えられる。
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
     */
    // 設定の Bean の名前は「接頭辞-完全修飾名」（@EnableConfigurationProperties の決まり）
    @Scheduled(fixedDelayString = "#{@'ecsite.cart.cleanup-"
            + "com.ecsite.module.cart.application.CartCleanupProperties'.interval()}")
    @SchedulerLock(name = "cart-cleanup")
    void cleanUp() {
        var expiredBefore = clock.instant().minus(properties.retention());
        ExpiredDataCleanup.run(log, "期限切れのカート", properties.batchSize(),
                () -> service.deleteExpired(expiredBefore, properties.batchSize()));
    }
}

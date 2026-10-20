package com.ecsite.module.inventory.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * 引当の片付けのジョブ（docs/backend/modules/inventory.md「引当」）。<br />
 * 期限から保存期間を過ぎた引当を、件数の上限ずつ、残りがなくなるまで消す（1 回ごとに別のトランザクション）。
 * 間隔と保存期間は引当の片付けの設定 {@code ecsite.inventory.cleanup}（既定 1 時間、1 日）で変えられる。
 */
@Slf4j
@Component
@RequiredArgsConstructor
class StockReservationCleanupJob {

    /**
     * 引当のサービス
     */
    private final StockReservationService service;

    /**
     * 引当の片付けの設定
     */
    private final StockReservationCleanupProperties properties;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * 期限から保存期間を過ぎた引当を消す。
     */
    // 設定の Bean の名前は「接頭辞-完全修飾名」（@EnableConfigurationProperties の決まり）
    @Scheduled(fixedDelayString = "#{@'ecsite.inventory.cleanup-"
            + "com.ecsite.module.inventory.application.StockReservationCleanupProperties'.interval()}")
    @SchedulerLock(name = "stock-reservation-cleanup")
    void cleanUp() {
        var expiredBefore = clock.instant().minus(properties.retention());
        long total = 0;
        int deleted;
        do {
            deleted = service.deleteExpired(expiredBefore, properties.batchSize());
            total += deleted;
        } while (deleted == properties.batchSize());
        if (total > 0) {
            log.info("期限切れの引当を消した：{} 件", total);
        } else {
            log.debug("期限切れの引当を消した：0 件");
        }
    }
}

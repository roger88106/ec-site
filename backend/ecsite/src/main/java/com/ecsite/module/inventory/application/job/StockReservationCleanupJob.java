package com.ecsite.module.inventory.application.job;

import com.ecsite.common.scheduling.ExpiredDataCleanup;
import com.ecsite.module.inventory.application.StockReservationCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * 引当の片付けのジョブ
 */
@Slf4j
@Component
@RequiredArgsConstructor
class StockReservationCleanupJob {

    /**
     * 引当のサービス
     */
    private final StockReservationCommandService service;

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
     *
     * <p>件数の上限ずつ、残りがなくなるまで消す。1 回ごとに別のトランザクションにする。
     */
    // 設定の Bean の名前は「接頭辞-完全修飾名」（@EnableConfigurationProperties の決まり）
    @Scheduled(fixedDelayString = "#{@'ecsite.inventory.cleanup-"
            + "com.ecsite.module.inventory.application.job.StockReservationCleanupProperties'.interval()}")
    @SchedulerLock(name = "stock-reservation-cleanup")
    void cleanUp() {
        var expiredBefore = clock.instant().minus(properties.retention());
        ExpiredDataCleanup.run(log, "期限切れの引当", properties.batchSize(),
                () -> service.deleteExpired(expiredBefore, properties.batchSize()));
    }
}

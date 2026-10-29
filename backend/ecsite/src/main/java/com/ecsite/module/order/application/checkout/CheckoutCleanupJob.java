package com.ecsite.module.order.application.checkout;

import com.ecsite.common.scheduling.ExpiredDataCleanup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * 注文手続きの片付けのジョブ（docs/03-機能仕様/order.md「注文手続き」）。<br />
 * 期限から保存期間を過ぎた注文手続きを、件数の上限ずつ、残りがなくなるまで消す（1 回ごとに別のトランザクション）。
 * 注文になった注文手続きも消す。注文の表示と操作は、注文手続きを読まない。
 * 間隔と保存期間は注文手続きの片付けの設定 {@code ecsite.order.cleanup}（既定 1 時間、1 日）で変えられる。
 */
@Slf4j
@Component
@RequiredArgsConstructor
class CheckoutCleanupJob {

    /**
     * 注文手続きのサービス
     */
    private final CheckoutService service;

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
    // 設定の Bean の名前は「接頭辞-完全修飾名」（@EnableConfigurationProperties の決まり）
    @Scheduled(fixedDelayString = "#{@'ecsite.order.cleanup-"
            + "com.ecsite.module.order.application.checkout.CheckoutCleanupProperties'.interval()}")
    @SchedulerLock(name = "checkout-cleanup")
    void cleanUp() {
        var expiredBefore = clock.instant().minus(properties.retention());
        ExpiredDataCleanup.run(log, "期限切れの注文手続き", properties.batchSize(),
                () -> service.deleteExpired(expiredBefore, properties.batchSize()));
    }
}

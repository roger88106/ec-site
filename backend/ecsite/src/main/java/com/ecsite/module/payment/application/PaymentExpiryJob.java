package com.ecsite.module.payment.application;

import com.ecsite.common.error.exception.SystemException;
import com.ecsite.module.payment.application.port.PaymentGateway;
import com.ecsite.module.payment.domain.payment.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 支払の期限切れのジョブ（docs/backend/modules/payment.md「支払の期限」）。<br />
 * 期限を過ぎた未払いの支払について、決済サービスの支払の画面を閉じてから、支払なしにする。
 * 閉じられなかった（その前に支払が済んでいた）支払は、与信の成功の通知を待つ。
 * 間隔は {@code ecsite.payment.expiry-interval}（既定 1 分）で変えられる。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentExpiryJob {

    /**
     * 1 回に処理する支払の数の上限（残りは次の回で処理する）
     */
    private static final int BATCH_SIZE = 100;

    /**
     * 支払の更新のサービス
     */
    private final PaymentCommandService commandService;

    /**
     * 決済サービス
     */
    private final PaymentGateway gateway;

    /**
     * 期限を過ぎた未払いの支払を、支払なしにする。<br />
     * 決済サービスの呼び出しはトランザクションの外で行い、1 つの支払の失敗で、ほかの支払を止めない。
     */
    @Scheduled(fixedDelayString = "${ecsite.payment.expiry-interval:PT1M}")
    @SchedulerLock(name = "payment-expiry")
    public void expire() {
        for (var payment : commandService.findPastDeadline(BATCH_SIZE)) {
            try {
                expire(payment);
            } catch (SystemException e) {
                // 決済サービスが応えない。次の回でやり直す
                log.warn("支払の期限切れを次の回に回す: paymentId={}", payment.id(), e);
            }
        }
    }

    /**
     * 1 つの支払を期限切れにする。
     */
    private void expire(Payment payment) {
        var page = payment.page();
        if (page.isPresent() && gateway.closePage(page.get().id()) == PaymentGateway.CloseResult.ALREADY_PAID) {
            log.info("期限の前に支払が済んでいたので、与信の成功の通知を待つ: paymentId={}", payment.id());
            return;
        }
        commandService.expire(payment.id());
    }
}

package com.ecsite.module.payment.application;

import com.ecsite.common.error.exception.SystemException;
import com.ecsite.module.payment.application.model.view.PastDeadlinePaymentView;
import com.ecsite.module.payment.application.port.PaymentGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 支払の期限切れのジョブ（docs/design/modules/payment.md「支払の期限」）。<br />
 * 期限を過ぎた未払いの支払について、決済サービスの支払画面を閉じてから、支払なしにする。
 * 閉じられなかった（その前に支払が済んでいた）支払は、与信の成功の通知を待つ支払にし、次の回から探さない。
 * 通知を長く待つ支払があれば、毎回 warn のログに件数を出す（人が扱うまで知らせ続ける）。
 * 間隔は決済の設定 {@code ecsite.payment.expiry-interval}（既定 1 分）で変えられる。
 */
@Slf4j
@Component
@RequiredArgsConstructor
class PaymentExpiryJob {

    /**
     * 1 回に処理する支払の数の上限（残りは次の回で処理する）
     */
    private static final int BATCH_SIZE = 100;

    /**
     * 支払の更新のサービス
     */
    private final PaymentCommandService commandService;

    /**
     * 支払の参照のサービス
     */
    private final PaymentQueryService queryService;

    /**
     * 決済サービス
     */
    private final PaymentGateway gateway;

    /**
     * 期限を過ぎた未払いの支払を、支払なしにする。<br />
     * 決済サービスの呼び出しはトランザクションの外で行い、1 つの支払の失敗（例外の種類を問わない）で、ほかの支払を止めない。
     */
    // 決済の設定の Bean の名前は「接頭辞-完全修飾名」（@EnableConfigurationProperties の決まり）
    @Scheduled(fixedDelayString = "#{@'ecsite.payment-com.ecsite.module.payment.application.PaymentProperties'"
            + ".expiryInterval()}")
    @SchedulerLock(name = "payment-expiry")
    public void expire() {
        for (var payment : queryService.findPastDeadline(BATCH_SIZE)) {
            try {
                expire(payment);
            } catch (SystemException e) {
                // 決済サービスが応えない。次の回でやり直す
                log.warn("支払の期限切れを次の回に回す: paymentId={}", payment.id(), e);
            } catch (RuntimeException e) {
                // 予期しない例外（決済サービスの応答、保存の失敗など）。次の回でやり直す
                log.warn("支払の期限切れに失敗したので、次の回に回す: paymentId={}", payment.id(), e);
            }
        }
        warnLongAwaitingAuthorization();
    }

    /**
     * 1 つの支払を期限切れにする。
     */
    private void expire(PastDeadlinePaymentView payment) {
        var page = payment.page();
        if (page != null && gateway.closePage(page.id()) == PaymentGateway.CloseResult.ALREADY_PAID) {
            log.info("期限の前に支払が済んでいたので、与信の成功の通知を待つ: paymentId={}", payment.id());
            commandService.awaitAuthorization(payment.id());
            return;
        }
        commandService.expire(payment.id());
    }

    /**
     * 与信の通知を長く待つ未払いの支払があれば、warn のログに件数を出す。
     */
    private void warnLongAwaitingAuthorization() {
        long count = queryService.countLongAwaitingAuthorization();
        if (count > 0) {
            log.warn("与信の通知を長く待つ支払があります: count={}", count);
        }
    }
}

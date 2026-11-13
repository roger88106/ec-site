package com.ecsite.module.payment.application.job;

import com.ecsite.common.error.exception.SystemException;
import com.ecsite.module.payment.application.PaymentGatewayService;
import com.ecsite.module.payment.application.PaymentQueryService;
import com.ecsite.module.payment.domain.PaymentErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 支払の期限切れのジョブ
 *
 * <p>1 件ずつの処理は {@link PaymentGatewayService} に任せ、ここでは対象の取得、失敗の切り分け、打ち切りを行う。
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
     * 与信の取得を打ち切る、決済サービスへの接続に続けて失敗した回数
     *
     * <p>応答がないと 1 件で最大約 75 秒かかるので、3 件（約 225 秒）続いたらその回の取得を止める。
     *
     * <p>断続的な失敗での取得の時間と、支払画面を閉じる処理の時間は制限しない。業務の正しさは、行ロックと冪等な遷移で保つ。
     */
    private static final int MAX_CONSECUTIVE_UNAVAILABLE = 3;

    /**
     * 支払の参照のサービス
     */
    private final PaymentQueryService queryService;

    /**
     * 決済ゲートウェイサービス
     */
    private final PaymentGatewayService gatewayService;

    /**
     * 期限を過ぎた未払いの支払を支払なしにし、与信の反映を待つ支払を与信済みにする。
     *
     * <p>与信の反映を長く待つ支払があれば、warn のログに件数を出す。
     */
    // 決済の設定の Bean の名前は「接頭辞-完全修飾名」（@EnableConfigurationProperties の決まり）
    @Scheduled(fixedDelayString = "#{@'ecsite.payment-com.ecsite.module.payment.application.PaymentProperties'"
            + ".expiryInterval()}")
    @SchedulerLock(name = "payment-expiry")
    public void expire() {
        // 期限切れ ※1 つの支払の失敗（例外の種類を問わない）で、ほかの支払を止めない
        for (var payment : queryService.findPastDeadline(BATCH_SIZE)) {
            try {
                gatewayService.expire(payment);
            } catch (RuntimeException e) {
                // 決済サービスの応答、保存の失敗、DB の値の破損のどれでも、次の回でやり直す
                log.warn("支払の期限切れに失敗したので、次の回に回す: paymentId={}", payment.id(), e);
            }
        }

        // 与信の取得 ※通知が届いていなくても進める
        recoverAuthorizations();

        // 長く待つ支払の警告 ※人が扱うまで、毎回知らせ続ける
        warnLongAwaitingAuthorization();
    }

    /**
     * 与信の反映を待つ支払について、決済サービスから与信を取得して与信済みにする。
     *
     * <p>取得できなかった支払は、次の回でやり直す。
     * 決済サービスへの接続の失敗が続いたら、その回の取得を打ち切る。
     */
    private void recoverAuthorizations() {
        int unavailable = 0;
        for (var payment : queryService.findAwaitingAuthorization(BATCH_SIZE)) {
            try {
                gatewayService.recoverAuthorization(payment);
                unavailable = 0;
            } catch (RuntimeException e) {
                log.warn("与信を取得できないので、次の回で再試行する: paymentId={}", payment.id(), e);
                unavailable = isUnavailable(e) ? unavailable + 1 : 0;
                if (unavailable >= MAX_CONSECUTIVE_UNAVAILABLE) {
                    log.warn("決済サービスへの接続の失敗が続いたので、この回の与信の取得を打ち切る: count={}", unavailable);
                    return;
                }
            }
        }
    }

    /**
     * 決済サービスへの接続の失敗（外部サービスのエラー）か。
     */
    private static boolean isUnavailable(RuntimeException e) {
        return e instanceof SystemException systemException
                && systemException.getErrorDetail().errorCode().equals(PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE.getCode());
    }

    /**
     * 与信の反映を長く待つ未払いの支払があれば、warn のログに件数を出す。
     */
    private void warnLongAwaitingAuthorization() {
        long count = queryService.countLongAwaitingAuthorization();
        if (count > 0) {
            log.warn("与信の反映を長く待つ支払がある: count={}", count);
        }
    }
}

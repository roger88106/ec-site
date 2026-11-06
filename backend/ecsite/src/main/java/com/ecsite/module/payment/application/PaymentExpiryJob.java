package com.ecsite.module.payment.application;

import com.ecsite.common.error.exception.SystemException;
import com.ecsite.module.payment.application.model.view.AwaitingAuthorizationPaymentView;
import com.ecsite.module.payment.application.model.view.PastDeadlinePaymentView;
import com.ecsite.module.payment.application.port.PaymentGateway;
import com.ecsite.module.payment.domain.PaymentErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 支払の期限切れのジョブ（docs/03-機能仕様/payment.md「支払の期限」）。<br />
 * 期限を過ぎた未払いの支払について、決済サービスの支払画面を閉じてから、支払なしにする。
 * 閉じられなかった（その前に支払が済んでいた）支払は、与信の反映を待つ支払にし、期限切れの検索から外す。
 * 与信の反映を待つ支払は、決済サービスから与信を取得して与信済みにする（通知が届いていなくても進める）。
 * 与信の反映を長く待つ支払があれば、毎回 warn のログに件数を出す（人が扱うまで知らせ続ける）。
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
     * 決済サービスへの接続の失敗が続いたら、その回の与信の取得を打ち切る件数。
     * 応答がないと 1 件で最大約 75 秒かかるので、この件数（約 225 秒）で止めて、ジョブのロックの上限（10 分）を超えないようにする
     */
    private static final int MAX_CONSECUTIVE_UNAVAILABLE = 3;

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
     * 期限を過ぎた未払いの支払を支払なしにし、与信の反映を待つ支払を与信済みにする。<br />
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
            } catch (RuntimeException e) {
                // 決済サービスの応答、保存の失敗、DB の値の破損のどれでも、次の回でやり直す
                log.warn("支払の期限切れに失敗したので、次の回に回す: paymentId={}", payment.id(), e);
            }
        }
        recoverAuthorizations();
        warnLongAwaitingAuthorization();
    }

    /**
     * 1 つの支払を期限切れにする。
     */
    private void expire(PastDeadlinePaymentView payment) {
        var page = payment.page();
        if (page != null && gateway.closePage(page.id()) == PaymentGateway.CloseResult.ALREADY_PAID) {
            log.info("期限の前に支払が済んでいたので、与信の反映を待つ: paymentId={}", payment.id());
            commandService.awaitAuthorization(payment.id());
            return;
        }
        commandService.expire(payment.id());
    }

    /**
     * 与信の反映を待つ支払について、決済サービスから与信を取得して与信済みにする。<br />
     * 取得できなかった支払は待つ支払のまま残し、次の回でやり直す。
     * 決済サービスへの接続の失敗が続いたら、その回の取得を打ち切る。
     */
    private void recoverAuthorizations() {
        int unavailable = 0;
        for (var payment : queryService.findAwaitingAuthorization(BATCH_SIZE)) {
            try {
                recoverAuthorization(payment);
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
     * 1 つの支払の与信を取得して反映する。
     */
    private void recoverAuthorization(AwaitingAuthorizationPaymentView payment) {
        var pageId = Objects.requireNonNull(payment.pageId(), "与信の反映を待つ支払には支払画面がある");
        var authorization = gateway.fetchAuthorization(pageId);
        if (commandService.authorizeFromGateway(payment.id(), authorization)) {
            log.warn("与信の通知が届いていないので、決済サービスから与信を取得して反映した: paymentId={}", payment.id());
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

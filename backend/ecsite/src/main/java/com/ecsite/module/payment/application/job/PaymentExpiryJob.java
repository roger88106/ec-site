package com.ecsite.module.payment.application.job;

import com.ecsite.common.error.exception.SystemException;
import com.ecsite.module.payment.application.PaymentGatewayService;
import com.ecsite.module.payment.application.PaymentQueryService;
import com.ecsite.module.payment.application.model.view.AwaitingAuthorizationPaymentView;
import com.ecsite.module.payment.application.model.view.CaptureReconcileView;
import com.ecsite.module.payment.domain.PaymentErrorCode;
import com.ecsite.module.payment.domain.payment.PaymentId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

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
     * 1 回に処理する支払の数の上限
     *
     * <p>残りは次の回で処理する。
     */
    private static final int BATCH_SIZE = 100;

    /**
     * 与信の取得と売上確定の照合を打ち切る、決済サービスへの接続に続けて失敗した回数
     *
     * <p>応答がないと 1 件で最大約 75 秒かかるので、この回数だけ続いたらその回の取得（照合）を止める。取得と照合は別に数える。
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
     * 期限を過ぎた未払いの支払を支払なしにし、与信の反映を待つ支払を与信済みにし、売上確定の結果が分からない支払を決済サービスの状態に合わせる。
     *
     * <p>与信の反映を長く待つ支払があれば、warn のログに件数を出す。
     * 各処理の検索が失敗したら、warn のログに出して次の処理へ進み、その処理は次の回で再試行する。
     */
    // 決済の設定の Bean の名前は「接頭辞-完全修飾名」（設定の型の登録の決まり）
    @Scheduled(fixedDelayString = "#{@'ecsite.payment-com.ecsite.module.payment.application.PaymentProperties'"
            + ".expiryInterval()}")
    // ロックを持つ時間の上限は 30 分。決済サービスが応答しないと 1 件で最大約 75 秒かかり、期限切れ・与信の取得・売上確定の照合がそれぞれ 1 回に最大 100 件なので、最悪の回は 30 分を超える。
    // 超えると別のインスタンスで同時に実行されうるが、業務の正しさは行ロックと冪等な状態の遷移で保つ。
    // 30 分は、通常の回が収まり、止まったインスタンスのロックが長く残らない値として選んだ
    @SchedulerLock(name = "payment-expiry", lockAtMostFor = "PT30M")
    public void expire() {
        // 期限切れ ※1 つの支払の失敗（例外の種類を問わない）で、ほかの支払を止めない
        var pastDeadline = search(() -> queryService.findPastDeadline(BATCH_SIZE),
                "期限切れの支払を検索できないので、次の回で再試行する");
        for (var payment : pastDeadline) {
            try {
                gatewayService.expire(payment);
            } catch (RuntimeException e) {
                // 決済サービスの応答、保存の失敗、DB の値の破損のどれでも、次の回でやり直す
                log.warn("支払の期限切れに失敗したので、次の回に回す: paymentId={}", payment.id(), e);
            }
        }

        // 与信の取得 ※通知が届いていなくても進める
        recoverAuthorizations();

        // 売上確定の照合 ※応答が届かなかった、反映の前に止まったなど、結果が分からない支払を決済サービスの状態に合わせる
        reconcileCaptures();

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
        processUntilUnavailable(
                search(() -> queryService.findAwaitingAuthorization(BATCH_SIZE), "与信の取得の対象を検索できないので、次の回で再試行する"),
                gatewayService::recoverAuthorization,
                AwaitingAuthorizationPaymentView::id, "与信を取得できないので、次の回で再試行する: paymentId={}",
                "決済サービスへの接続の失敗が続いたので、この回の与信の取得を打ち切る: count={}");
    }

    /**
     * 売上確定の結果が分からない支払について、決済サービスの状態と照合する。
     *
     * <p>照合できなかった支払は、次の回でやり直す。
     * 決済サービスへの接続の失敗が続いたら、その回の照合を打ち切る。
     */
    private void reconcileCaptures() {
        processUntilUnavailable(
                search(() -> queryService.findForCaptureReconcile(BATCH_SIZE), "売上確定の照合の対象を検索できないので、次の回で再試行する"),
                gatewayService::reconcileCapture,
                CaptureReconcileView::id, "売上確定の結果を照合できないので、次の回で再試行する: paymentId={}",
                "決済サービスへの接続の失敗が続いたので、この回の売上確定の照合を打ち切る: count={}");
    }

    /**
     * 支払を 1 件ずつ処理する。1 件の失敗では止めず、決済サービスへの接続の失敗が続いたら打ち切る。
     *
     * @param payments       処理する支払
     * @param processor      1 件の処理
     * @param idOf           支払 ID を取り出す関数
     * @param failureMessage 1 件が失敗したときの warn のログの書式（引数は支払 ID）
     * @param abortMessage   打ち切りの warn のログの書式（引数は続けて失敗した件数）
     * @param <T>            支払の型
     */
    private <T> void processUntilUnavailable(List<T> payments, Consumer<T> processor, Function<T, PaymentId> idOf,
                                             String failureMessage, String abortMessage) {
        int unavailable = 0;
        for (var payment : payments) {
            try {
                processor.accept(payment);
                unavailable = 0;
            } catch (RuntimeException e) {
                log.warn(failureMessage, idOf.apply(payment), e);
                unavailable = isUnavailable(e) ? unavailable + 1 : 0;
                if (unavailable >= MAX_CONSECUTIVE_UNAVAILABLE) {
                    log.warn(abortMessage, unavailable);
                    return;
                }
            }
        }
    }

    /**
     * 処理の対象を検索する。検索が失敗したら、warn のログに失敗を出し、空の一覧を返して次の処理へ進む。
     *
     * <p>その処理は次の回で再試行する。1 件ずつの処理の失敗は捕捉しない（検索の呼び出しだけを囲む）。
     *
     * @param query          検索
     * @param failureMessage 検索が失敗したときの warn のログの文
     * @param <T>            支払の型
     */
    private <T> List<T> search(Supplier<List<T>> query, String failureMessage) {
        try {
            return query.get();
        } catch (RuntimeException e) {
            log.warn(failureMessage, e);
            return List.of();
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
        long count;
        try {
            count = queryService.countLongAwaitingAuthorization();
        } catch (RuntimeException e) {
            log.warn("与信の反映を長く待つ支払の件数を集計できないので、次の回で再試行する", e);
            return;
        }
        if (count > 0) {
            log.warn("与信の反映を長く待つ支払がある: count={}", count);
        }
    }
}

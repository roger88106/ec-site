package com.ecsite.module.payment.application;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.error.exception.SystemException;
import com.ecsite.module.payment.application.model.view.AwaitingAuthorizationPaymentView;
import com.ecsite.module.payment.application.model.view.CaptureReconcileView;
import com.ecsite.module.payment.application.model.view.PastDeadlinePaymentView;
import com.ecsite.module.payment.application.port.PaymentGateway;
import com.ecsite.module.payment.domain.PaymentErrorCode;
import com.ecsite.module.payment.domain.payment.PaymentPage;
import com.ecsite.module.payment.domain.payment.PaymentStatus;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 決済ゲートウェイサービス
 *
 * <p>決済サービスはトランザクションの外で呼び、支払の読み取りと結果の反映は、
 * 参照と更新のサービスのトランザクションで行う。決済サービスの応答を待つ間に、
 * 行ロックと DB の接続を持たないため。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentGatewayService {

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
     * 決済の設定
     */
    private final PaymentProperties properties;

    /**
     * 支払画面の URL を返す。
     *
     * <p>開いている画面があればそれを、なければ決済サービスに作らせる。支払画面は、1 つの支払に同時に 1 つだけ開く。
     * トランザクションの外で呼ぶ（トランザクションの中から呼ぶと例外）。
     *
     * @param orderId 注文 ID
     * @return 支払画面の URL。支払がない、支払えない（未払いでない、期限の後）なら空
     * @throws SystemException 決済サービスに接続できない（{@code PAYMENT_GATEWAY_UNAVAILABLE}）
     */
    @Transactional(propagation = Propagation.NEVER)
    public Optional<String> pageUrl(@NonNull UUID orderId) {
        // 支払取得
        var payable = queryService.findPayable(orderId);
        if (payable.isEmpty()) {
            return Optional.empty();
        }
        var payment = payable.get();
        if (payment.page() != null) {
            return Optional.of(payment.page().url());
        }

        // 支払画面作成
        var opened = gateway.openPage(new PaymentGateway.PageRequest(payment.id(), orderId, payment.amount(),
                properties.returnUrlOf(orderId)));

        // 支払画面の記録
        var used = commandService.recordPage(orderId, opened);
        // 使わない画面（同時のリクエストに先を越された、その間に支払えなくなった）は、支払えないように閉じる
        if (used.filter(page -> page.id().equals(opened.id())).isEmpty()) {
            gateway.closePage(opened.id());
        }
        return used.map(PaymentPage::url);
    }

    /**
     * 注文の支払の売上を確定する。
     *
     * <p>決済サービスを呼ぶ前に、支払を行ロックして読み、売上確定を求めた時刻を記録する（1 つのトランザクションでコミットする）。
     * 売上確定済みなら、決済サービスを呼ばずに成功する。
     * トランザクションの外で呼ぶ（トランザクションの中から呼ぶと例外）。
     *
     * @param orderId 注文 ID
     * @throws BusinessException 支払がない（{@code NOT_FOUND}）
     * @throws BusinessException 売上を確定できる状態でない（{@code PAYMENT_NOT_CAPTURABLE}）
     * @throws BusinessException 与信の期限が切れている（{@code AUTHORIZATION_EXPIRED}）
     * @throws SystemException   決済サービスに接続できない（{@code PAYMENT_GATEWAY_UNAVAILABLE}）。支払は与信済みのまま残る
     */
    @Transactional(propagation = Propagation.NEVER)
    public void capture(@NonNull UUID orderId) {
        // 支払取得と、売上確定を求めた時刻の記録 ※支払を行ロックして読み、1 つのトランザクションで記録する。
        // 決済サービスの呼び出しの前にコミットし、確定の後に止まっても照合の対象に残す
        var payment = commandService.requestCapture(orderId)
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND));
        switch (payment.status()) {
            case CAPTURED -> {
                return;
            }
            case AUTHORIZATION_EXPIRED -> throw BusinessException.of(PaymentErrorCode.AUTHORIZATION_EXPIRED);
            case AUTHORIZED -> {
                // 決済サービスに売上確定を求める
            }
            default -> throw BusinessException.of(PaymentErrorCode.PAYMENT_NOT_CAPTURABLE);
        }

        // 売上確定
        var result = gateway.capture(new PaymentGateway.CaptureRequest(orderId,
                Objects.requireNonNull(payment.authorizationRef(), "売上を確定できる支払には与信の参照がある"),
                payment.amount()));

        // 結果の反映
        switch (result) {
            case CAPTURED -> commandService.markCaptured(payment.id());
            case AUTHORIZATION_EXPIRED -> {
                commandService.markAuthorizationExpired(payment.id());
                throw BusinessException.of(PaymentErrorCode.AUTHORIZATION_EXPIRED);
            }
            case UNREACHABLE -> throw SystemException.of("決済サービスに接続できず、売上を確定できない: orderId=" + orderId, null,
                    PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
        }
    }

    /**
     * 期限を過ぎた未払いの支払を、支払画面を閉じてから支払なしにする。
     *
     * <p>閉じる前に支払が済んでいたら、支払なしにせず、与信の反映を待つ支払にする（期限切れの検索から外れる）。
     * 読んだ後に支払画面が記録された支払は、画面を閉じていないので、その回は支払なしにしない（次の回で閉じる）。
     *
     * @param payment 期限を過ぎた支払
     * @throws SystemException 決済サービスに接続できない（{@code PAYMENT_GATEWAY_UNAVAILABLE}）
     */
    public void expire(@NonNull PastDeadlinePaymentView payment) {
        // 支払画面を閉じる ※閉じる前に支払が済んでいたら、与信の反映を待つ支払にする
        var page = payment.page();
        if (page != null && gateway.closePage(page.id()) == PaymentGateway.CloseResult.ALREADY_PAID) {
            log.info("期限の前に支払が済んでいたので、与信の反映を待つ: paymentId={}", payment.id());
            commandService.awaitAuthorization(payment.id());
            return;
        }

        // 支払なしにする
        commandService.expire(payment.id(), page == null ? null : page.id());
    }

    /**
     * 与信の反映を待つ支払について、決済サービスから与信を取得して与信済みにする。
     *
     * @param payment 与信の反映を待つ支払
     * @throws SystemException 決済サービスに接続できない（{@code PAYMENT_GATEWAY_UNAVAILABLE}）
     */
    public void recoverAuthorization(@NonNull AwaitingAuthorizationPaymentView payment) {
        // 与信取得
        var pageId = Objects.requireNonNull(payment.pageId(), "与信の反映を待つ支払には支払画面がある");
        var authorization = gateway.fetchAuthorization(pageId);

        // 与信の反映
        if (commandService.authorizeFromGateway(payment.id(), authorization)) {
            log.warn("通知より先に、決済サービスから与信を取得して反映した: paymentId={}", payment.id());
        }
    }

    /**
     * 売上確定の結果が分からない支払について、決済サービスの売上確定の状態を取得し、支払の状態を決済サービスに合わせる。
     *
     * <p>決済サービスを読むだけで、売上確定を求めない。支払の変え方は、決済サービスの状態で決める。
     * <ul>
     *   <li>売上確定済み：売上確定済みにし、warn のログに支払 ID を出力する。
     *       読んだ後に注文がキャンセルされて与信の取消待ちになっていれば、返金待ちにして、別の文の warn のログを出力する</li>
     *   <li>与信が切れた：与信期限切れにする</li>
     *   <li>売上を確定していない：売上確定を求めた時刻を削除し、与信済みのまま残す
     *       （読んだ後に売上確定が再実行されて時刻が変わっていれば、削除しない）</li>
     * </ul>
     *
     * @param payment 照合の対象の支払
     * @throws SystemException 決済サービスに接続できない（{@code PAYMENT_GATEWAY_UNAVAILABLE}）
     */
    public void reconcileCapture(@NonNull CaptureReconcileView payment) {
        // 売上確定の状態の取得
        var state = gateway.fetchCaptureState(payment.authorizationRef());

        // 結果の反映
        switch (state) {
            case CAPTURED -> {
                // 読んだ後に注文がキャンセルされていれば、売上確定済みでなく返金待ちになる
                commandService.markCaptured(payment.id()).ifPresent(changed -> {
                    if (changed == PaymentStatus.CAPTURED) {
                        log.warn("売上確定の結果が分からなかった支払を、決済サービスに合わせて売上確定済みにした: paymentId={}", payment.id());
                    } else {
                        log.warn("売上確定の結果が分からなかった支払は、注文がキャンセルされていたので、返金に進めた: paymentId={}",
                                payment.id());
                    }
                });
            }
            case AUTHORIZATION_EXPIRED -> commandService.markAuthorizationExpired(payment.id());
            case NOT_CAPTURED -> {
                if (commandService.clearCaptureRequest(payment.id(), payment.captureRequestedAt())) {
                    log.info("決済サービスで売上を確定していなかったので、売上確定を求めた時刻を削除した: paymentId={}", payment.id());
                }
            }
        }
    }

    /**
     * 決済サービスで与信を取り消してから、与信の取消済みにする。
     *
     * <p>与信の取消待ちでなければ何もしない。
     * 売上を確定していて取り消せなければ、返金待ちにして、返金の依頼を登録する。
     *
     * @param paymentId 支払 ID
     */
    void voidAuthorization(UUID paymentId) {
        queryService.findAuthorizationToVoid(paymentId).ifPresent(authorizationRef -> {
            switch (gateway.voidAuthorization(authorizationRef)) {
                case VOIDED -> commandService.markVoided(paymentId);
                // 確定した与信は取り消せないので、売上確定を反映して返金に進める
                case ALREADY_CAPTURED -> commandService.markCaptured(paymentId);
            }
        });
    }

    /**
     * 支払なしの支払の支払画面を、決済サービスで閉じる。
     *
     * <p>支払なしでない、または支払画面がなければ何もしない。
     * 閉じる前に支払が済んでいたら、決済サービスから与信を取得して反映する（与信の取消待ちになり、与信の取消の依頼を登録する）。
     *
     * @param paymentId 支払 ID
     * @throws SystemException 決済サービスに接続できない（{@code PAYMENT_GATEWAY_UNAVAILABLE}）
     */
    void closePage(UUID paymentId) {
        queryService.findPageToClose(paymentId).ifPresent(pageId -> {
            if (gateway.closePage(pageId) == PaymentGateway.CloseResult.ALREADY_PAID) {
                log.info("支払画面を閉じる前に支払が済んでいたので、与信を反映して取り消す: paymentId={}", paymentId);
                commandService.authorizeFromGateway(paymentId, gateway.fetchAuthorization(pageId));
            }
        });
    }

    /**
     * 決済サービスで返金してから、返金済みにする。
     *
     * <p>返金待ちでなければ何もしない。
     *
     * @param paymentId 支払 ID
     */
    void refund(UUID paymentId) {
        queryService.findRefundRequest(paymentId).ifPresent(request -> {
            gateway.refund(request);
            commandService.markRefunded(paymentId);
        });
    }
}

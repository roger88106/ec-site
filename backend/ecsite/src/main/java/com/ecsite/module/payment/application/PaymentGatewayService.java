package com.ecsite.module.payment.application;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.error.exception.SystemException;
import com.ecsite.module.payment.application.port.PaymentGateway;
import com.ecsite.module.payment.domain.PaymentErrorCode;
import com.ecsite.module.payment.domain.payment.PaymentPage;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 決済サービスを呼ぶ操作のサービス
 *
 * <p>支払画面、売上確定、与信の取消、返金を扱う。決済サービスはトランザクションの外で呼び、
 * 支払の読み取りと結果の反映は、参照と更新のサービスのトランザクションで行う。決済サービスの応答を待つ間に、
 * 行ロックと DB の接続を持たないため。
 */
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
     *
     * @param orderId 注文 ID
     * @return 支払画面の URL。支払がない、支払えない（未払いでない、期限の後）なら空
     * @throws SystemException 決済サービスに接続できない（PAYMENT_GATEWAY_UNAVAILABLE）
     */
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
     * <p>売上確定済みなら、決済サービスを呼ばずに成功する。
     *
     * @param orderId 注文 ID
     * @throws BusinessException 支払がない（NOT_FOUND）
     * @throws BusinessException 売上を確定できる状態でない（PAYMENT_NOT_CAPTURABLE）
     * @throws BusinessException 与信の期限が切れている（AUTHORIZATION_EXPIRED）
     * @throws SystemException   決済サービスに接続できない（PAYMENT_GATEWAY_UNAVAILABLE）。支払は与信済みのまま残る
     */
    public void capture(@NonNull UUID orderId) {
        // 支払取得
        var payment = queryService.findForCapture(orderId)
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
        var result = gateway.capture(
                new PaymentGateway.CaptureRequest(orderId,
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

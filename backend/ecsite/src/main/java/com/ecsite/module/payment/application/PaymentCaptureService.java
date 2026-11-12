package com.ecsite.module.payment.application;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.error.exception.SystemException;
import com.ecsite.module.payment.application.port.PaymentGateway;
import com.ecsite.module.payment.domain.PaymentErrorCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

/**
 * 売上確定のサービス
 *
 * <p>決済サービスはトランザクションの外で呼び、結果の反映だけを支払の更新のサービスのトランザクションで行う。
 * 決済サービスの応答を待つ間、行ロックを持たないため。
 */
@Service
@RequiredArgsConstructor
public class PaymentCaptureService {

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
}

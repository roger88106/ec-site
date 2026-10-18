package com.ecsite.module.payment.application;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.error.exception.SystemException;
import com.ecsite.module.payment.application.port.PaymentGateway;
import com.ecsite.module.payment.domain.payment.PaymentErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 売上確定のサービス（docs/backend/modules/payment.md「売上確定」）。<br />
 * 決済サービスの応答を待つ間、行ロックを持たないよう、決済サービスの呼び出しはトランザクションの外で行い、
 * 結果の反映だけを {@link PaymentCommandService} のトランザクションで行う。
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
     * 注文の支払の売上を確定する。売上確定済みなら、決済サービスを呼ばずに成功する。
     *
     * @param orderId 注文 ID
     * @throws BusinessException 支払がない、売上を確定できる状態でない、与信の期限が切れている
     * @throws SystemException   決済サービスに接続できない（支払は与信済みのまま残る）
     */
    public void capture(UUID orderId) {
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
        var result = gateway.capture(
                new PaymentGateway.CaptureRequest(orderId, payment.authorizationRef(), payment.amount()));
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

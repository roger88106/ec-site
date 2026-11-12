package com.ecsite.module.payment.application;

import com.ecsite.module.payment.application.port.PaymentGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 与信の取消と返金のサービス
 *
 * <p>決済サービスはトランザクションの外で呼び、読み取りと更新は、支払の参照と更新のサービスのトランザクションで行う。
 * 決済サービスを待つ間に、DB の接続を持たないため。
 */
@Service
@RequiredArgsConstructor
class PaymentReversalService {

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

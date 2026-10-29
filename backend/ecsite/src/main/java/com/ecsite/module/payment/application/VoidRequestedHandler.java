package com.ecsite.module.payment.application;

import com.ecsite.module.payment.application.port.PaymentGateway;
import com.ecsite.module.payment.domain.payment.VoidRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;

/**
 * 与信の取消の依頼のリスナー（docs/03-機能仕様/payment.md「与信の取消」）。<br />
 * コミットの後に非同期で、決済サービスで与信を取り消し、与信の取消済みにする。
 * 決済サービスはトランザクションの外で呼び（DB の接続を持ったまま待たない）、読み取りと更新は参照・更新のサービスのトランザクションで行う。
 * 決済サービスが失敗したら例外で終わり、イベントは未完了で残って、再発行のジョブがやり直す。
 * 取消済みの支払には何もしないので、何度届いても結果は同じ。
 */
@Component
@RequiredArgsConstructor
class VoidRequestedHandler {

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
     * 与信の取消の依頼：決済サービスで取り消してから、与信の取消済みにする。
     *
     * @param event 与信の取消の依頼
     */
    @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
    void on(VoidRequested event) {
        queryService.findAuthorizationToVoid(event.paymentId()).ifPresent(authorizationRef -> {
            gateway.voidAuthorization(authorizationRef);
            commandService.markVoided(event.paymentId());
        });
    }
}

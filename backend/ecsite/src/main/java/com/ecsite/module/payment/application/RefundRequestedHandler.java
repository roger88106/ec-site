package com.ecsite.module.payment.application;

import com.ecsite.module.payment.application.port.PaymentGateway;
import com.ecsite.module.payment.domain.payment.RefundRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;

/**
 * 返金の依頼のリスナー（docs/03-機能仕様/payment.md「返金」）。<br />
 * コミットの後に非同期で、決済サービスで返金し、返金済みにする。
 * 決済サービスはトランザクションの外で呼び（DB の接続を持ったまま待たない）、読み取りと更新は参照・更新のサービスのトランザクションで行う。
 * 決済サービスが失敗したら例外で終わり、イベントは未完了で残って、再発行のジョブがやり直す。
 * 返金待ちでない支払には何もしないので、何度届いても結果は同じ。
 */
@Component
@RequiredArgsConstructor
class RefundRequestedHandler {

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
     * 返金の依頼：決済サービスで返金してから、返金済みにする。
     *
     * @param event 返金の依頼
     */
    @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
    void on(RefundRequested event) {
        queryService.findRefundRequest(event.paymentId()).ifPresent(request -> {
            gateway.refund(request);
            commandService.markRefunded(event.paymentId());
        });
    }
}

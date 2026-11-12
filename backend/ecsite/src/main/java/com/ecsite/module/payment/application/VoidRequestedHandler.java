package com.ecsite.module.payment.application;

import com.ecsite.module.payment.domain.payment.VoidRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;

/**
 * 与信の取消の依頼のハンドラー
 */
@Component
@RequiredArgsConstructor
class VoidRequestedHandler {

    /**
     * 与信の取消と返金のサービス
     */
    private final PaymentReversalService reversalService;

    /**
     * 与信の取消の依頼：決済サービスで与信を取り消してから、与信の取消済みにする。
     *
     * <p>コミットの後に受ける。トランザクションを始めない（決済サービスを待つ間に DB の接続を持たない）。
     * 決済サービスが失敗したら例外で終わり、イベントは未完了で残って、再発行でやり直す。
     *
     * @param event 与信の取消の依頼
     */
    @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
    void on(VoidRequested event) {
        reversalService.voidAuthorization(event.paymentId());
    }
}

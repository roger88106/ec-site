package com.ecsite.module.order.application.payment;

import com.ecsite.module.payment.event.PaymentAuthorized;
import com.ecsite.module.payment.event.PaymentExpired;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 決済のイベントのリスナー（docs/03-機能仕様/order.md「支払」）。<br />
 * コミット後に非同期で、別のトランザクションで動く。何度届いても結果は同じ。
 * 処理に失敗したら、未完了のイベントの再発行でやり直す。
 */
@Component
@RequiredArgsConstructor
class PaymentEventHandler {

    /**
     * 注文の支払の結果のサービス
     */
    private final OrderPaymentService orderPaymentService;

    /**
     * 与信の成功：注文を受付にし、注文した品目をカートから除く。
     *
     * @param event 与信の成功
     */
    @ApplicationModuleListener
    void on(PaymentAuthorized event) {
        orderPaymentService.accept(event.orderId());
    }

    /**
     * 支払の期限切れ：注文をキャンセル（支払期限切れ）にし、在庫を戻す。
     *
     * @param event 支払の期限切れ
     */
    @ApplicationModuleListener
    void on(PaymentExpired event) {
        orderPaymentService.expire(event.orderId());
    }
}

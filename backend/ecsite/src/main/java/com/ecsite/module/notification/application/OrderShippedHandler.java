package com.ecsite.module.notification.application;

import com.ecsite.module.order.event.OrderShipped;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 注文の出荷のハンドラー
 */
@Component
@RequiredArgsConstructor
class OrderShippedHandler {

    /**
     * 出荷通知メールのサービス
     */
    private final ShipmentMailService shipmentMailService;

    /**
     * 注文の出荷：出荷通知メールを送る。
     *
     * <p>注文のコミットの後に、新しいトランザクションで受ける。送信に失敗したら、イベントは未完了のまま残る。
     *
     * @param event 注文の出荷
     */
    @ApplicationModuleListener
    void on(OrderShipped event) {
        shipmentMailService.send(event);
    }
}

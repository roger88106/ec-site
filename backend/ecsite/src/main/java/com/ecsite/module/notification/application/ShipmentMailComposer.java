package com.ecsite.module.notification.application;

import com.ecsite.common.message.MessageHelper;
import com.ecsite.module.notification.application.port.CarrierNameDirectory;
import com.ecsite.module.notification.application.port.Mailer;
import com.ecsite.module.order.event.OrderShipped;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 出荷通知メールを組み立てる。件名と本文の雛形は messages.properties の ecsite.notification.shipment.* にある。
 */
@Component
@RequiredArgsConstructor
public class ShipmentMailComposer {

    /**
     * メッセージヘルパー
     */
    private final MessageHelper messageHelper;

    /**
     * 配送業者の表示名のキー
     */
    private final CarrierNameDirectory carrierNameDirectory;

    /**
     * 注文の出荷から、出荷通知メールを組み立てる。宛先は注文のメールアドレス。
     *
     * @param event 注文の出荷
     * @return メール
     */
    public Mailer.Mail compose(OrderShipped event) {
        var carrierName = messageHelper.getMessage(carrierNameDirectory.nameKey(event.carrier()), Map.of());
        var params = Map.of(
                "orderNumber", event.orderNumber(),
                "carrier", carrierName,
                "trackingNumber", event.trackingNumber());
        return new Mailer.Mail(event.email(),
                messageHelper.getMessage("ecsite.notification.shipment.subject", params),
                messageHelper.getMessage("ecsite.notification.shipment.body", params));
    }
}

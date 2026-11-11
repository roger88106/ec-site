package com.ecsite.module.notification.application;

import com.ecsite.common.message.MessageHelper;
import com.ecsite.module.notification.application.port.Mailer;
import com.ecsite.module.notification.application.port.SentMailRepository;
import com.ecsite.module.notification.application.port.ShippingDirectory;
import com.ecsite.module.order.event.OrderShipped;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;

/**
 * 出荷通知メールのサービス
 */
@Service
@RequiredArgsConstructor
class ShipmentMailService {

    /**
     * 送ったメールの記録
     */
    private final SentMailRepository sentMails;

    /**
     * メールの送信
     */
    private final Mailer mailer;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * メッセージヘルパー
     */
    private final MessageHelper messageHelper;

    /**
     * 配送業者の表示名の文言のキーの取得
     */
    private final ShippingDirectory shippingDirectory;

    /**
     * まだ送っていなければ、出荷通知メールを記録して送る。
     *
     * <p>同じ注文には 1 通だけ送る。送ったメールの記録と送信を同じトランザクションで行う。
     *
     * @param event 注文の出荷
     * @throws RuntimeException 送れなかったとき（送ったメールの記録は巻き戻る）
     */
    @Transactional
    void send(OrderShipped event) {
        if (!sentMails.recordIfAbsent(event.orderId(), SentMailRepository.Kind.SHIPMENT, event.email(), clock.instant())) {
            return;
        }
        mailer.send(compose(event));
    }

// ============================== プライベートメソッド ==============================

    /**
     * 注文の出荷から、出荷通知メールを組み立てる。
     *
     * <p>宛先は注文のメールアドレス。件名と本文の雛形は messages.properties の ecsite.notification.shipment.* にある。
     */
    private Mailer.Mail compose(OrderShipped event) {
        var carrierName = messageHelper.getMessage(shippingDirectory.nameKey(event.carrier()), Map.of());
        var params = Map.of(
                "orderNumber", event.orderNumber(),
                "carrier", carrierName,
                "trackingNumber", event.trackingNumber());
        return new Mailer.Mail(event.email(),
                messageHelper.getMessage("ecsite.notification.shipment.subject", params),
                messageHelper.getMessage("ecsite.notification.shipment.body", params));
    }
}

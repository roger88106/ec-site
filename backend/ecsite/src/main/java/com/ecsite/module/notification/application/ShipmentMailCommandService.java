package com.ecsite.module.notification.application;

import com.ecsite.common.message.MessageResolver;
import com.ecsite.module.notification.application.port.Mailer;
import com.ecsite.module.notification.application.port.OrderDirectory;
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
@Transactional
class ShipmentMailCommandService {

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
     * メッセージの解決
     */
    private final MessageResolver messageResolver;

    /**
     * 配送業者の表示名の文言のキーの取得
     */
    private final ShippingDirectory shippingDirectory;

    /**
     * 注文のメールアドレスの取得
     */
    private final OrderDirectory orderDirectory;

    /**
     * まだ送っていなければ、出荷通知メールを記録して送る。
     *
     * <p>同じ注文には 1 通だけ送る。送ったメールの記録と送信を同じトランザクションで行う。
     *
     * @param event 注文の出荷
     */
    void send(OrderShipped event) {
        // 宛先 ※注文が見つからなければ例外で、記録も送信もしない
        var email = orderDirectory.email(event.orderId());
        // 送信記録 ※記録済みなら送らない
        if (!sentMails.recordIfAbsent(event.orderId(), SentMailRepository.Kind.SHIPMENT, email, clock.instant())) {
            return;
        }
        // メール送信
        mailer.send(compose(event, email));
    }

// ============================== プライベートメソッド ==============================

    /**
     * 注文の出荷から、出荷通知メールを組み立てる。
     *
     * <p>宛先は、注文の API から読んだ注文のメールアドレス。件名と本文の雛形は messages.properties の ecsite.notification.shipment.* にある。
     *
     * @param event 注文の出荷
     * @param email 宛先（注文のメールアドレス）
     */
    private Mailer.Mail compose(OrderShipped event, String email) {
        var carrierName = messageResolver.getMessage(shippingDirectory.nameKey(event.carrier()), Map.of());
        var params = Map.of(
                "orderNumber", event.orderNumber(),
                "carrier", carrierName,
                "trackingNumber", event.trackingNumber());
        return new Mailer.Mail(email,
                messageResolver.getMessage("ecsite.notification.shipment.subject", params),
                messageResolver.getMessage("ecsite.notification.shipment.body", params));
    }
}

package com.ecsite.module.notification.application;

import com.ecsite.module.notification.application.port.Mailer;
import com.ecsite.module.notification.application.port.SentMailRepository;
import com.ecsite.module.order.event.OrderShipped;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * 出荷通知メールのサービス
 *
 * <p>同じ注文には 1 通だけ送る。送ったメールの記録と送信を同じトランザクションで行い、
 * 送信に失敗したら記録ごと巻き戻す。
 */
@Service
@RequiredArgsConstructor
class ShipmentMailService {

    /**
     * 送ったメールの記録
     */
    private final SentMailRepository sentMails;

    /**
     * 出荷通知メールの組み立て
     */
    private final ShipmentMailComposer composer;

    /**
     * メールの送信
     */
    private final Mailer mailer;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * まだ送っていなければ、出荷通知メールを記録して送る。
     *
     * @param event 注文の出荷
     */
    @Transactional
    void send(OrderShipped event) {
        if (!sentMails.recordIfAbsent(event.orderId(), SentMailRepository.Kind.SHIPMENT, event.email(), clock.instant())) {
            return;
        }
        mailer.send(composer.compose(event));
    }
}

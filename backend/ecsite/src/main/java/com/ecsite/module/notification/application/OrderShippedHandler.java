package com.ecsite.module.notification.application;

import com.ecsite.module.notification.application.port.Mailer;
import com.ecsite.module.notification.application.port.SentMailRepository;
import com.ecsite.module.order.event.OrderShipped;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * 注文の出荷を受けて、出荷通知メールを送る（docs/03-機能仕様/notification.md「出荷通知メール」）。<br />
 * 注文のコミットの後に、新しいトランザクションで受ける。送ったメールの記録と送信を同じトランザクションで行い、
 * 送信に失敗したら記録ごと巻き戻して、未完了のイベントの再発行で送り直す。
 */
@Component
@RequiredArgsConstructor
class OrderShippedHandler {

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
     * 注文の出荷：まだ送っていなければ、出荷通知メールを記録して送る。
     *
     * @param event 注文の出荷
     */
    @ApplicationModuleListener
    void on(OrderShipped event) {
        if (!sentMails.recordIfAbsent(event.orderId(), SentMailRepository.Kind.SHIPMENT, event.email(), clock.instant())) {
            return;
        }
        mailer.send(composer.compose(event));
    }
}

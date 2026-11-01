package com.ecsite.module.notification.application.port;

import java.time.Instant;
import java.util.UUID;

/**
 * 送ったメールの記録のリポジトリ（docs/03-機能仕様/notification.md の「1 通だけ送る」）
 */
public interface SentMailRepository {

    /**
     * 送ったメールを記録する。同じ注文と種類の記録がすでにあれば、何もしない。<br />
     * 同時に記録しようとしたら、後の記録は先のトランザクションの終わりを待ってから決まる。
     *
     * @param orderId   注文 ID
     * @param kind      メールの種類
     * @param recipient 宛先
     * @param sentAt    送った日時
     * @return 記録したら true。すでに記録があれば false
     */
    boolean recordIfAbsent(UUID orderId, Kind kind, String recipient, Instant sentAt);

    /**
     * メールの種類
     */
    enum Kind {

        /**
         * 出荷通知メール
         */
        SHIPMENT,
    }
}

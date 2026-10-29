package com.ecsite.module.notification.application.port;

import com.ecsite.module.order.event.OrderShipped;

/**
 * 配送業者の表示名の文言のキーを得る port（docs/03-機能仕様/notification.md の依存の表（※2））。<br />
 * 表示名は出荷モジュールが決めるので、実装は出荷モジュールの API から得る。
 */
public interface ShippingDirectory {

    /**
     * 配送業者の表示名の文言のキーを返す。
     *
     * @param carrier 注文の出荷イベントの配送業者
     * @return 文言のキー
     */
    String nameKey(OrderShipped.Carrier carrier);
}

package com.ecsite.module.notification.application.port;

import com.ecsite.module.order.event.OrderShipped;

/**
 * 配送業者の表示名の文言のキーの取得
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

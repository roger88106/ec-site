package com.ecsite.module.order.domain.order;

import com.ecsite.module.order.event.OrderShipped;

/**
 * 配送業者
 */
public enum Carrier {

    /**
     * ヤマト運輸
     */
    YAMATO,

    /**
     * 佐川急便
     */
    SAGAWA,

    /**
     * 日本郵便
     */
    JAPAN_POST,
    ;

    /**
     * 配送業者を、イベントの配送業者にする。
     *
     * @return イベントの配送業者
     */
    public OrderShipped.Carrier toEventCarrier() {
        return switch (this) {
            case YAMATO -> OrderShipped.Carrier.YAMATO;
            case SAGAWA -> OrderShipped.Carrier.SAGAWA;
            case JAPAN_POST -> OrderShipped.Carrier.JAPAN_POST;
        };
    }
}

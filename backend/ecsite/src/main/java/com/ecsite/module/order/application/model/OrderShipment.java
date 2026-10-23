package com.ecsite.module.order.application.model;

import com.ecsite.module.order.domain.order.Carrier;

import java.time.Instant;

/**
 * 注文の出荷の表示
 *
 * @param carrier        配送業者
 * @param trackingNumber 送り状番号（ハイフンを除いた数字）
 * @param status         配送状態
 * @param shippedAt      出荷日時
 */
public record OrderShipment(Carrier carrier, String trackingNumber, Status status, Instant shippedAt) {

    /**
     * 配送状態が返送かを返す。
     *
     * @return 返送なら true
     */
    public boolean isReturned() {
        return status == Status.RETURNED;
    }

    /**
     * 配送状態（docs/design/modules/shipping.md）
     */
    public enum Status {

        /**
         * 出荷済み
         */
        SHIPPED,

        /**
         * 配送中
         */
        IN_TRANSIT,

        /**
         * 配達完了
         */
        DELIVERED,

        /**
         * 返送
         */
        RETURNED,
    }
}

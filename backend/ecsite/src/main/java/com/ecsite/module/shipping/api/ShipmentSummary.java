package com.ecsite.module.shipping.api;

import java.time.Instant;
import java.util.UUID;

/**
 * 注文に公開する出荷の情報。
 *
 * @param orderId        注文 ID
 * @param carrier        配送業者
 * @param trackingNumber 送り状番号（ハイフンを除いた数字）
 * @param status         配送状態
 * @param shippedAt      出荷日時
 */
public record ShipmentSummary(UUID orderId, Carrier carrier, String trackingNumber, Status status, Instant shippedAt) {

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
    }

    /**
     * 配送状態
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

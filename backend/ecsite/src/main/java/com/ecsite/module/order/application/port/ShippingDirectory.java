package com.ecsite.module.order.application.port;

import com.ecsite.module.order.domain.order.Carrier;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 出荷の腐敗防止層（docs/design/modules/order.md「出荷の登録」）
 */
public interface ShippingDirectory {

    /**
     * 注文の出荷を作る。呼び出し元のトランザクションに参加する。
     *
     * @param orderId        注文 ID
     * @param carrier        配送業者
     * @param trackingNumber 送り状番号（ハイフンは出荷モジュールが除く）
     * @param shippedAt      出荷日時
     * @return 作った出荷
     */
    Shipment create(UUID orderId, Carrier carrier, String trackingNumber, Instant shippedAt);

    /**
     * 注文の出荷をまとめて返す。
     *
     * @param orderIds 注文 ID
     * @return 注文 ID → 出荷。出荷のない注文は含まない
     */
    Map<UUID, Shipment> find(Collection<UUID> orderIds);

    /**
     * 配送状態が返送の注文 ID を返す。
     *
     * @return 注文 ID
     */
    List<UUID> returnedOrderIds();

    /**
     * 注文の出荷
     *
     * @param carrier        配送業者
     * @param trackingNumber 送り状番号（ハイフンを除いた数字）
     * @param status         配送状態
     * @param shippedAt      出荷日時
     */
    record Shipment(Carrier carrier, String trackingNumber, Status status, Instant shippedAt) {

        /**
         * 配送状態が返送かを返す。
         *
         * @return 返送なら true
         */
        public boolean isReturned() {
            return status == Status.RETURNED;
        }
    }

    /**
     * 配送状態（docs/design/modules/shipping.md）
     */
    enum Status {

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

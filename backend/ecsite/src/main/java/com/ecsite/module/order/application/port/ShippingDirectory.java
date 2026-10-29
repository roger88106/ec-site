package com.ecsite.module.order.application.port;

import com.ecsite.module.order.application.model.OrderShipment;
import com.ecsite.module.order.domain.order.Carrier;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 出荷の腐敗防止層（docs/03-機能仕様/order.md「出荷の登録」）
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
    OrderShipment create(UUID orderId, Carrier carrier, String trackingNumber, Instant shippedAt);

    /**
     * 注文の出荷をまとめて返す。
     *
     * @param orderIds 注文 ID
     * @return 注文 ID → 出荷。出荷のない注文は含まない
     */
    Map<UUID, OrderShipment> find(Collection<UUID> orderIds);

    /**
     * 配送状態が返送の注文 ID を返す。
     *
     * @return 注文 ID
     */
    List<UUID> returnedOrderIds();
}

package com.ecsite.module.order.application.port;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.model.id.OrderId;
import com.ecsite.module.order.application.model.view.OrderShipment;
import com.ecsite.module.order.domain.order.Carrier;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 出荷の腐敗防止層の port
 */
public interface ShippingDirectory {

    /**
     * 注文の出荷を作る。
     *
     * <p>呼んだ側のトランザクションに加わる。
     *
     * @param orderId        注文 ID
     * @param carrier        配送業者
     * @param trackingNumber 送り状番号（ハイフンは出荷モジュールが除く）
     * @param shippedAt      出荷日時
     * @return 作った出荷
     * @throws BusinessException 送り状番号が形に合わない（{@code TRACKING_NUMBER_INVALID}）
     * @throws BusinessException 同じ注文にすでに出荷がある（{@code SHIPMENT_ALREADY_EXISTS}）
     */
    OrderShipment create(OrderId orderId, Carrier carrier, String trackingNumber, Instant shippedAt);

    /**
     * 注文の出荷をまとめて返す。
     *
     * @param orderIds 注文 ID
     * @return 注文 ID → 出荷。出荷のない注文は含まない
     */
    Map<OrderId, OrderShipment> find(Collection<OrderId> orderIds);

    /**
     * 配送状態が返送の注文 ID を返す。
     *
     * @return 注文 ID
     */
    List<OrderId> returnedOrderIds();
}

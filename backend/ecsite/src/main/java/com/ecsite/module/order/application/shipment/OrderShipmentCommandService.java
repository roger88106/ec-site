package com.ecsite.module.order.application.shipment;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.order.application.model.OrderShipment;
import com.ecsite.module.order.application.port.ShippingDirectory;
import com.ecsite.module.order.domain.order.Carrier;
import com.ecsite.module.order.domain.OrderErrorCode;
import com.ecsite.module.order.domain.order.port.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * 出荷の登録の更新のサービス。売上確定の後に、注文を出荷済みにして出荷を作る（{@link OrderShipmentService} から呼ぶ）。
 */
@Service
@RequiredArgsConstructor
class OrderShipmentCommandService {

    /**
     * 注文リポジトリ
     */
    private final OrderRepository orderRepository;

    /**
     * 出荷の腐敗防止層
     */
    private final ShippingDirectory shippingDirectory;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * 注文を行ロックし、受付なら出荷済みにして、同じトランザクションで出荷を作る。
     *
     * @param orderId        注文 ID
     * @param carrier        配送業者
     * @param trackingNumber 送り状番号
     * @return 作った出荷
     * @throws BusinessException 売上確定の後に、注文が受付でなくなっていた（支払は売上確定済みのまま残る）
     */
    @Transactional
    public OrderShipment ship(UUID orderId, Carrier carrier, String trackingNumber) {
        var order = orderRepository.lockById(orderId).orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND));
        if (!order.isShippable()) {
            throw BusinessException.of(OrderErrorCode.ORDER_NOT_SHIPPABLE);
        }
        var now = clock.instant();
        // 送り状番号は出荷モジュールが検証してハイフンを除くので、先に出荷を作り、除いた番号をイベントに載せる
        var shipment = shippingDirectory.create(orderId, carrier, trackingNumber, now);
        order.ship(carrier, shipment.trackingNumber(), now);
        orderRepository.update(order);
        return shipment;
    }
}

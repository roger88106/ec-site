package com.ecsite.module.order.infrastructure.directory;

import com.ecsite.common.conversion.EnumConversion;
import com.ecsite.module.order.application.model.view.OrderShipment;
import com.ecsite.module.order.application.port.ShippingDirectory;
import com.ecsite.module.order.domain.order.Carrier;
import com.ecsite.module.shipping.api.ShippingApi;
import com.ecsite.module.shipping.api.model.ShipmentSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 出荷の腐敗防止層
 *
 * <p>出荷モジュールの API を呼ぶ。
 */
@Component
@RequiredArgsConstructor
class ShippingApiDirectory implements ShippingDirectory {

    /**
     * 出荷の API
     */
    private final ShippingApi shippingApi;

    /**
     * {@inheritDoc}
     */
    @Override
    public OrderShipment create(UUID orderId, Carrier carrier, String trackingNumber, Instant shippedAt) {
        return toShipment(shippingApi.create(orderId, toShippingCarrier(carrier), trackingNumber, shippedAt));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<UUID, OrderShipment> find(Collection<UUID> orderIds) {
        return shippingApi.find(orderIds).stream()
                .collect(Collectors.toMap(ShipmentSummary::orderId, ShippingApiDirectory::toShipment));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<UUID> returnedOrderIds() {
        return shippingApi.returnedOrderIds();
    }

    /**
     * 出荷モジュールの型を、注文の型にする。
     */
    private static OrderShipment toShipment(ShipmentSummary summary) {
        return new OrderShipment(toCarrier(summary.carrier()), summary.trackingNumber(), toStatus(summary.status()),
                summary.shippedAt());
    }

    /**
     * 配送業者を、出荷モジュールの型にする。
     */
    static ShipmentSummary.Carrier toShippingCarrier(Carrier carrier) {
        return EnumConversion.byName(carrier, ShipmentSummary.Carrier.class);
    }

    /**
     * 出荷モジュールの配送業者を、注文の型にする。
     */
    static Carrier toCarrier(ShipmentSummary.Carrier carrier) {
        return EnumConversion.byName(carrier, Carrier.class);
    }

    /**
     * 出荷モジュールの配送状態を、注文の型にする。
     */
    static OrderShipment.Status toStatus(ShipmentSummary.Status status) {
        return EnumConversion.byName(status, OrderShipment.Status.class);
    }
}

package com.ecsite.module.order.infrastructure.directory;

import com.ecsite.module.order.application.port.ShippingDirectory;
import com.ecsite.module.order.domain.order.Carrier;
import com.ecsite.module.shipping.api.ShipmentSummary;
import com.ecsite.module.shipping.api.ShippingApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 出荷の腐敗防止層の実装。出荷モジュールの API を呼ぶ。
 */
@Component
@RequiredArgsConstructor
class ShippingApiDirectory implements ShippingDirectory {

    /**
     * 出荷の API
     */
    private final ShippingApi shippingApi;

    @Override
    public Shipment create(UUID orderId, Carrier carrier, String trackingNumber, Instant shippedAt) {
        return toShipment(shippingApi.create(orderId, ShipmentSummary.Carrier.valueOf(carrier.name()), trackingNumber, shippedAt));
    }

    @Override
    public Map<UUID, Shipment> find(Collection<UUID> orderIds) {
        return shippingApi.find(orderIds).stream()
                .collect(Collectors.toMap(ShipmentSummary::orderId, ShippingApiDirectory::toShipment));
    }

    @Override
    public List<UUID> returnedOrderIds() {
        return shippingApi.returnedOrderIds();
    }

    /**
     * 出荷モジュールの型を、注文の型にする。
     */
    private static Shipment toShipment(ShipmentSummary summary) {
        return new Shipment(Carrier.valueOf(summary.carrier().name()), summary.trackingNumber(),
                Status.valueOf(summary.status().name()), summary.shippedAt());
    }
}

package com.ecsite.module.shipping.api;

import com.ecsite.module.shipping.application.ShipmentService;
import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.domain.DeliveryStatus;
import com.ecsite.module.shipping.domain.Shipment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 注文に公開する、出荷の API（docs/design/modules/shipping.md「他のモジュールへの公開」）。<br />
 * 出荷を作るのは、呼び出し元（注文）のトランザクションに参加する。
 */
@Component
@RequiredArgsConstructor
public class ShippingApi {

    /**
     * 出荷のサービス
     */
    private final ShipmentService shipmentService;

    /**
     * 注文の出荷を作る。配送状態は「出荷済み」。同じ注文で重ねて作ると、業務のエラー（409）になる。
     *
     * @param orderId        注文 ID
     * @param carrier        配送業者
     * @param trackingNumber 送り状番号（前後の空白とハイフンは除いて保存する）
     * @param shippedAt      出荷日時
     * @return 作った出荷
     */
    public ShipmentSummary create(UUID orderId, ShipmentSummary.Carrier carrier, String trackingNumber, Instant shippedAt) {
        return toSummary(shipmentService.create(orderId, toCarrier(carrier), trackingNumber, shippedAt));
    }

    /**
     * 注文の出荷をまとめて返す。
     *
     * @param orderIds 注文 ID
     * @return 出荷。出荷のない注文は含まない
     */
    public List<ShipmentSummary> find(Collection<UUID> orderIds) {
        return shipmentService.find(orderIds).stream().map(ShippingApi::toSummary).toList();
    }

    /**
     * 配送状態が「返送」の出荷の注文 ID を返す。
     *
     * @return 注文 ID
     */
    public List<UUID> returnedOrderIds() {
        return shipmentService.returnedOrderIds();
    }

    /**
     * 出荷を公開の型にする。
     */
    private static ShipmentSummary toSummary(Shipment shipment) {
        return new ShipmentSummary(
                shipment.orderId(),
                toSummaryCarrier(shipment.carrier()),
                shipment.trackingNumber().value(),
                toSummaryStatus(shipment.status()),
                shipment.shippedAt());
    }

    /**
     * 公開の型の配送業者を、ドメインの型にする。
     */
    private static Carrier toCarrier(ShipmentSummary.Carrier carrier) {
        return switch (carrier) {
            case YAMATO -> Carrier.YAMATO;
            case SAGAWA -> Carrier.SAGAWA;
            case JAPAN_POST -> Carrier.JAPAN_POST;
        };
    }

    /**
     * 配送業者を公開の型にする。
     */
    private static ShipmentSummary.Carrier toSummaryCarrier(Carrier carrier) {
        return switch (carrier) {
            case YAMATO -> ShipmentSummary.Carrier.YAMATO;
            case SAGAWA -> ShipmentSummary.Carrier.SAGAWA;
            case JAPAN_POST -> ShipmentSummary.Carrier.JAPAN_POST;
        };
    }

    /**
     * 配送状態を公開の型にする。
     */
    private static ShipmentSummary.Status toSummaryStatus(DeliveryStatus status) {
        return switch (status) {
            case SHIPPED -> ShipmentSummary.Status.SHIPPED;
            case IN_TRANSIT -> ShipmentSummary.Status.IN_TRANSIT;
            case DELIVERED -> ShipmentSummary.Status.DELIVERED;
            case RETURNED -> ShipmentSummary.Status.RETURNED;
        };
    }
}

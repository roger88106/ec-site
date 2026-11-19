package com.ecsite.module.shipping.api;

import com.ecsite.common.conversion.EnumConversion;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.shipping.api.model.ShipmentSummary;
import com.ecsite.module.shipping.application.ShipmentService;
import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.domain.DeliveryStatus;
import com.ecsite.module.shipping.domain.Shipment;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 出荷の API
 */
@Component
@RequiredArgsConstructor
public class ShippingApi {

    /**
     * 出荷のサービス
     */
    private final ShipmentService shipmentService;

    /**
     * 注文の出荷を作る。
     *
     * <p>配送状態は「出荷済み」。呼んだ側のトランザクションに加わる（トランザクションの外から呼ぶと例外）。
     *
     * @param orderId        注文 ID
     * @param carrier        配送業者
     * @param trackingNumber 送り状番号（前後の空白とハイフンは除いて保存する）。null のときは TRACKING_NUMBER_INVALID の業務のエラーにする
     * @param shippedAt      出荷日時
     * @return 作った出荷
     * @throws BusinessException 送り状番号が形に合わない（{@code TRACKING_NUMBER_INVALID}）
     * @throws BusinessException 同じ注文にすでに出荷がある（{@code SHIPMENT_ALREADY_EXISTS}）
     */
    public ShipmentSummary create(@NonNull UUID orderId, @NonNull ShipmentSummary.Carrier carrier, @Nullable String trackingNumber,
                                  @NonNull Instant shippedAt) {
        return toSummary(shipmentService.create(orderId, toCarrier(carrier), trackingNumber, shippedAt));
    }

    /**
     * 注文の出荷をまとめて返す。
     *
     * @param orderIds 注文 ID
     * @return 出荷。出荷のない注文は含まない
     */
    public List<ShipmentSummary> find(@NonNull Collection<UUID> orderIds) {
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
     * 配送業者の表示名の文言のキーを返す。
     *
     * @param carrier 配送業者
     * @return 文言のキー
     */
    public String carrierNameKey(@NonNull ShipmentSummary.Carrier carrier) {
        return toCarrier(carrier).nameMessageKey();
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
    static Carrier toCarrier(ShipmentSummary.Carrier carrier) {
        return EnumConversion.byName(carrier, Carrier.class);
    }

    /**
     * 配送業者を公開の型にする。
     */
    static ShipmentSummary.Carrier toSummaryCarrier(Carrier carrier) {
        return EnumConversion.byName(carrier, ShipmentSummary.Carrier.class);
    }

    /**
     * 配送状態を公開の型にする。
     */
    static ShipmentSummary.Status toSummaryStatus(DeliveryStatus status) {
        return EnumConversion.byName(status, ShipmentSummary.Status.class);
    }
}

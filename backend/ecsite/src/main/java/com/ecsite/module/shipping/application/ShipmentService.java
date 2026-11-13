package com.ecsite.module.shipping.application;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.shipping.application.port.CarrierTracking;
import com.ecsite.module.shipping.application.port.ShipmentRepository;
import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.domain.Shipment;
import com.ecsite.module.shipping.domain.TrackingNumber;
import com.ecsite.module.shipping.event.DeliveryStatusChanged;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 出荷のサービス
 */
@Service
@RequiredArgsConstructor
public class ShipmentService {

    /**
     * 出荷のリポジトリ
     */
    private final ShipmentRepository shipmentRepository;

    /**
     * 物流サービスの追跡
     */
    private final CarrierTracking tracking;

    /**
     * イベントの発行
     */
    private final ApplicationEventPublisher events;

    /**
     * 出荷を作る。
     *
     * <p>配送状態は「出荷済み」で、配送状態の変化のイベントを出す。呼んだ側のトランザクションに加わる。
     *
     * @param orderId        注文 ID
     * @param carrier        配送業者
     * @param trackingNumber 送り状番号の入力
     * @param shippedAt      出荷日時
     * @return 作った出荷
     * @throws BusinessException 送り状番号が形に合わない（TRACKING_NUMBER_INVALID）
     * @throws BusinessException 同じ注文にすでに出荷がある（SHIPMENT_ALREADY_EXISTS）
     */
    @Transactional
    public Shipment create(@NonNull UUID orderId, @NonNull Carrier carrier, String trackingNumber,
                           @NonNull Instant shippedAt) {
        // 出荷登録
        var shipment = Shipment.create(UUID.randomUUID(), orderId, carrier, TrackingNumber.of(trackingNumber), shippedAt);
        shipmentRepository.insert(shipment);

        // 配送状態の変化の発行
        events.publishEvent(DeliveryStatusChanged.of(shippedAt, orderId, null, shipment.status().toEventStatus()));

        // 追跡の開始の依頼の発行 ※物流サービスへの追跡の登録は、コミットの後に行う（失敗しても出荷を止めない）
        events.publishEvent(new TrackingRequested(shipment.id(), carrier, shipment.trackingNumber().value()));
        return shipment;
    }

    /**
     * 注文 ID で出荷をまとめて読む。
     *
     * @param orderIds 注文 ID
     * @return 出荷。出荷のない注文は含めない
     */
    @Transactional(readOnly = true)
    public List<Shipment> find(@NonNull Collection<UUID> orderIds) {
        return shipmentRepository.findAllByOrderIds(orderIds);
    }

    /**
     * 配送状態が「返送」の出荷の注文 ID を返す。
     *
     * @return 注文 ID
     */
    @Transactional(readOnly = true)
    public List<UUID> returnedOrderIds() {
        return shipmentRepository.findReturnedOrderIds();
    }

    /**
     * 物流サービスに追跡を始めさせる。
     *
     * @param event 追跡の開始の依頼
     */
    void startTracking(TrackingRequested event) {
        tracking.startTracking(event.carrier(), event.trackingNumber());
    }
}

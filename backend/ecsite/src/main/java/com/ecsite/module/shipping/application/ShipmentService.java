package com.ecsite.module.shipping.application;

import com.ecsite.module.shipping.application.port.ShipmentRepository;
import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.domain.Shipment;
import com.ecsite.module.shipping.domain.TrackingNumber;
import com.ecsite.module.shipping.event.DeliveryStatusChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 出荷のサービス（docs/03-機能仕様/shipping.md）。<br />
 * 出荷を作ることと、出荷を読むことを受け持つ。物流サービスの通知の反映は、通知のサービスが受け持つ。
 */
@Service
@RequiredArgsConstructor
public class ShipmentService {

    /**
     * 出荷のリポジトリ
     */
    private final ShipmentRepository shipmentRepository;

    /**
     * イベントの発行
     */
    private final ApplicationEventPublisher events;

    /**
     * 出荷を作る。配送状態は「出荷済み」で、配送状態の変化のイベントを出す。呼び出し元のトランザクションに参加する。
     *
     * @param orderId        注文 ID
     * @param carrier        配送業者
     * @param trackingNumber 送り状番号の入力
     * @param shippedAt      出荷日時
     * @return 作った出荷
     * @throws com.ecsite.common.error.exception.BusinessException 送り状番号が形に合わないとき、同じ注文にすでに出荷があるとき
     */
    @Transactional
    public Shipment create(UUID orderId, Carrier carrier, String trackingNumber, Instant shippedAt) {
        var shipment = Shipment.create(UUID.randomUUID(), orderId, carrier, TrackingNumber.of(trackingNumber), shippedAt);
        shipmentRepository.insert(shipment);
        events.publishEvent(DeliveryStatusChanged.of(shippedAt, orderId, null, DeliveryStatusChanged.Status.SHIPPED));
        // 物流サービスへの追跡の登録は、コミットの後に行う（失敗しても出荷を止めない）
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
    public List<Shipment> find(Collection<UUID> orderIds) {
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
}

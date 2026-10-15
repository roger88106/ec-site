package com.ecsite.module.shipping.application;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.shipping.application.port.ShipmentNotificationReceiver;
import com.ecsite.module.shipping.application.port.ShipmentRepository;
import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.domain.DeliveryStatus;
import com.ecsite.module.shipping.domain.TrackingNumber;
import com.ecsite.module.shipping.event.DeliveryStatusChanged;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * 物流サービスの通知の反映（docs/backend/modules/shipping.md「配送状態と順位」）。<br />
 * 出荷の行をロックしてから進めるので、同じ出荷への通知が同時に届いても、順位が高い状態が残る。
 * 進めたときだけ、配送状態の変化のイベントを出す。
 */
@Slf4j
@Service
@RequiredArgsConstructor
class ShipmentNotificationService implements ShipmentNotificationReceiver {

    /**
     * 出荷のリポジトリ
     */
    private final ShipmentRepository shipmentRepository;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * イベントの発行
     */
    private final ApplicationEventPublisher events;

    @Override
    @Transactional
    public Result receive(Carrier carrier, String trackingNumber, DeliveryStatus status) {
        var normalized = normalize(trackingNumber);
        if (normalized == null) {
            return Result.NOT_FOUND;
        }
        var shipments = shipmentRepository.findAllByTrackingForUpdate(carrier, normalized);
        if (shipments.isEmpty()) {
            return Result.NOT_FOUND;
        }
        var now = clock.instant();
        var result = Result.UNCHANGED;
        for (var shipment : shipments) {
            var from = shipment.status();
            if (shipment.advanceTo(status, now)) {
                shipmentRepository.updateProgress(shipment);
                events.publishEvent(DeliveryStatusChanged.of(now, shipment.orderId(), toEventStatus(from), toEventStatus(status)));
                result = Result.ADVANCED;
            }
        }
        return result;
    }

    /**
     * 配送状態を、イベントの配送状態にする。
     */
    private static DeliveryStatusChanged.Status toEventStatus(DeliveryStatus status) {
        return DeliveryStatusChanged.Status.valueOf(status.name());
    }

    /**
     * 通知の送り状番号を、保存と同じ形にする。形に合わなければ、どの出荷にも合わないので null。
     */
    private static TrackingNumber normalize(String trackingNumber) {
        try {
            return TrackingNumber.of(trackingNumber);
        } catch (BusinessException e) {
            log.warn("形に合わない送り状番号の通知を無視する: {}", trackingNumber);
            return null;
        }
    }
}

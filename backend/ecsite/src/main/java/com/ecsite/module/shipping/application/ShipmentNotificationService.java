package com.ecsite.module.shipping.application;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.shipping.application.port.ShipmentNotificationReceiver;
import com.ecsite.module.shipping.application.port.ShipmentRepository;
import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.domain.DeliveryStatus;
import com.ecsite.module.shipping.domain.TrackingNumber;
import com.ecsite.module.shipping.event.DeliveryStatusChanged;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * 配送状態の通知のサービス
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
    public Result receive(@NonNull Carrier carrier, String trackingNumber, @NonNull DeliveryStatus status) {
        // 出荷取得
        var normalized = normalize(trackingNumber);
        if (normalized == null) {
            return Result.NOT_FOUND;
        }
        // 行をロックしてから進めるので、同じ出荷への通知が同時に届いても、順位が高い状態が残る
        var shipments = shipmentRepository.findAllByTrackingForUpdate(carrier, normalized);
        if (shipments.isEmpty()) {
            return Result.NOT_FOUND;
        }

        // 配送状態の更新と、配送状態の変化の発行
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

// ============================== プライベートメソッド ==============================

    /**
     * 配送状態を、イベントの配送状態にする。
     */
    private static DeliveryStatusChanged.Status toEventStatus(DeliveryStatus status) {
        return switch (status) {
            case SHIPPED -> DeliveryStatusChanged.Status.SHIPPED;
            case IN_TRANSIT -> DeliveryStatusChanged.Status.IN_TRANSIT;
            case DELIVERED -> DeliveryStatusChanged.Status.DELIVERED;
            case RETURNED -> DeliveryStatusChanged.Status.RETURNED;
        };
    }

    /**
     * 通知の送り状番号を、保存と同じ形にする。形に合わなければ null。
     */
    private static @Nullable TrackingNumber normalize(String trackingNumber) {
        try {
            return TrackingNumber.of(trackingNumber);
        } catch (BusinessException e) {
            // 形に合わない番号は、どの出荷にも合わないので、出荷がない通知として扱う
            log.warn("形に合わない送り状番号の通知を無視する: {}", trackingNumber);
            return null;
        }
    }
}

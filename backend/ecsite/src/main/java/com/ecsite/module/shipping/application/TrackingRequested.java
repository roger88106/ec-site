package com.ecsite.module.shipping.application;

import com.ecsite.common.model.base.DomainEvent;
import com.ecsite.module.shipping.domain.Carrier;

import java.time.Instant;
import java.util.UUID;

/**
 * 追跡の開始の依頼
 *
 * <p>出荷のコミットの後に追跡を始めるための、出荷モジュールの中だけで使うイベント。
 * 出荷と同じトランザクションで記録されるので、物流サービスが失敗しても、出荷を止めずに再発行でやり直せる。
 *
 * @param eventId        イベント ID
 * @param occurredAt     発生時刻
 * @param shipmentId     出荷 ID
 * @param carrier        配送業者
 * @param trackingNumber 送り状番号（ハイフンを除いた数字）
 */
record TrackingRequested(UUID eventId, Instant occurredAt, UUID shipmentId, Carrier carrier, String trackingNumber)
        implements DomainEvent {

    /**
     * イベントを作る。
     *
     * @param occurredAt     発生時刻
     * @param shipmentId     出荷 ID
     * @param carrier        配送業者
     * @param trackingNumber 送り状番号（ハイフンを除いた数字）
     * @return イベント
     */
    static TrackingRequested of(Instant occurredAt, UUID shipmentId, Carrier carrier, String trackingNumber) {
        return new TrackingRequested(UUID.randomUUID(), occurredAt, shipmentId, carrier, trackingNumber);
    }
}

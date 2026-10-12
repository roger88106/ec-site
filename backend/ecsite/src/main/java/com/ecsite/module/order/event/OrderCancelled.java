package com.ecsite.module.order.event;

import com.ecsite.common.model.base.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * 注文キャンセル。今は購読者がない（決済・出荷・レポートの土台）。
 *
 * @param eventId    イベント ID
 * @param occurredAt 発生時刻
 * @param orderId    注文 ID
 */
public record OrderCancelled(UUID eventId, Instant occurredAt, UUID orderId) implements DomainEvent {

    /**
     * イベントを作る。
     *
     * @param occurredAt 発生時刻
     * @param orderId    注文 ID
     * @return イベント
     */
    public static OrderCancelled of(Instant occurredAt, UUID orderId) {
        return new OrderCancelled(UUID.randomUUID(), occurredAt, orderId);
    }
}

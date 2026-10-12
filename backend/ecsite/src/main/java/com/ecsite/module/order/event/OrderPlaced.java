package com.ecsite.module.order.event;

import com.ecsite.common.model.base.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * 注文成立。今は購読者がない（決済・出荷・レポートの土台。plan 002 第 4 節）。
 *
 * @param eventId    イベント ID
 * @param occurredAt 発生時刻
 * @param orderId    注文 ID
 */
public record OrderPlaced(UUID eventId, Instant occurredAt, UUID orderId) implements DomainEvent {

    /**
     * イベントを作る。
     *
     * @param occurredAt 発生時刻
     * @param orderId    注文 ID
     * @return イベント
     */
    public static OrderPlaced of(Instant occurredAt, UUID orderId) {
        return new OrderPlaced(UUID.randomUUID(), occurredAt, orderId);
    }
}

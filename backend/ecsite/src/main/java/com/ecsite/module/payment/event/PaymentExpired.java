package com.ecsite.module.payment.event;

import com.ecsite.common.model.base.DomainEvent;
import com.ecsite.common.model.id.OrderId;

import java.time.Instant;
import java.util.UUID;

/**
 * 支払の期限切れ
 *
 * @param eventId    イベント ID
 * @param occurredAt 発生時刻
 * @param orderId    注文 ID
 */
public record PaymentExpired(UUID eventId, Instant occurredAt, OrderId orderId) implements DomainEvent {

    /**
     * イベントを作る。
     *
     * @param occurredAt 発生時刻
     * @param orderId    注文 ID
     * @return イベント
     */
    public static PaymentExpired of(Instant occurredAt, OrderId orderId) {
        return new PaymentExpired(DomainEvent.newEventId(), occurredAt, orderId);
    }
}

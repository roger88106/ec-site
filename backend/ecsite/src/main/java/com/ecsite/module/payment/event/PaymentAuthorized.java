package com.ecsite.module.payment.event;

import com.ecsite.common.model.base.DomainEvent;
import com.ecsite.common.model.id.OrderId;

import java.time.Instant;
import java.util.UUID;

/**
 * 与信の成功
 *
 * <p>期限切れの後に届いた与信では出さない（決済の中で取り消す）。
 *
 * @param eventId    イベント ID
 * @param occurredAt 発生時刻
 * @param orderId    注文 ID
 */
public record PaymentAuthorized(UUID eventId, Instant occurredAt, OrderId orderId) implements DomainEvent {

    /**
     * イベントを作る。
     *
     * @param occurredAt 発生時刻
     * @param orderId    注文 ID
     * @return イベント
     */
    public static PaymentAuthorized of(Instant occurredAt, OrderId orderId) {
        return new PaymentAuthorized(DomainEvent.newEventId(), occurredAt, orderId);
    }
}

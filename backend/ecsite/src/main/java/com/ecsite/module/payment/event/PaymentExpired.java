package com.ecsite.module.payment.event;

import com.ecsite.common.model.base.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * 支払の期限切れ。注文が受けて、キャンセル（支払期限切れ）にし、在庫を戻す。
 *
 * @param eventId    イベント ID
 * @param occurredAt 発生時刻
 * @param orderId    注文 ID
 */
public record PaymentExpired(UUID eventId, Instant occurredAt, UUID orderId) implements DomainEvent {

    /**
     * イベントを作る。
     *
     * @param occurredAt 発生時刻
     * @param orderId    注文 ID
     * @return イベント
     */
    public static PaymentExpired of(Instant occurredAt, UUID orderId) {
        return new PaymentExpired(UUID.randomUUID(), occurredAt, orderId);
    }
}

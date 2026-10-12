package com.ecsite.module.cart.event;

import com.ecsite.common.model.base.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * カート変更。注文モジュールが、そのカートの注文手続きの引当を解く。
 *
 * @param eventId    イベント ID
 * @param occurredAt 発生時刻
 * @param cartId     カート ID
 */
public record CartChanged(UUID eventId, Instant occurredAt, UUID cartId) implements DomainEvent {

    /**
     * イベントを作る。
     *
     * @param occurredAt 発生時刻
     * @param cartId     カート ID
     * @return イベント
     */
    public static CartChanged of(Instant occurredAt, UUID cartId) {
        return new CartChanged(UUID.randomUUID(), occurredAt, cartId);
    }
}

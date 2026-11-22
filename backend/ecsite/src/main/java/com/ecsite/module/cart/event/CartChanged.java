package com.ecsite.module.cart.event;

import com.ecsite.common.model.base.DomainEvent;
import com.ecsite.common.model.id.CartId;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * カート変更
 *
 * <p>カートの品目か数量が変わったときに発行する。
 *
 * @param eventId     イベント ID
 * @param occurredAt  発生時刻
 * @param cartId      カート ID
 * @param cartVersion 変更の後のカートのバージョン。null は、バージョンを加える前に発行されたイベント（記録に残って再発行されるもの）
 */
public record CartChanged(UUID eventId, Instant occurredAt, CartId cartId, @Nullable Long cartVersion) implements DomainEvent {

    /**
     * イベントを作る。
     *
     * @param occurredAt  発生時刻
     * @param cartId      カート ID
     * @param cartVersion 変更の後のカートのバージョン
     * @return イベント
     */
    public static CartChanged of(Instant occurredAt, CartId cartId, long cartVersion) {
        return new CartChanged(DomainEvent.newEventId(), occurredAt, cartId, cartVersion);
    }
}

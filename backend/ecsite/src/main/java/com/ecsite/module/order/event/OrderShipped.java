package com.ecsite.module.order.event;

import com.ecsite.common.model.base.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * 注文の出荷
 *
 * <p>出荷を知らせるのに必要な値（注文 ID、注文番号、配送業者、送り状番号）を載せる。
 * 宛先は載せない。受ける側が、注文 ID で注文の API から読む。
 *
 * @param eventId        イベント ID
 * @param occurredAt     発生時刻（出荷日時）
 * @param orderId        注文 ID
 * @param orderNumber    注文番号
 * @param carrier        配送業者
 * @param trackingNumber 送り状番号（ハイフンを除いた数字）
 */
public record OrderShipped(UUID eventId, Instant occurredAt, UUID orderId, String orderNumber,
                           Carrier carrier, String trackingNumber) implements DomainEvent {

    /**
     * イベントを作る。
     *
     * @param occurredAt     発生時刻（出荷日時）
     * @param orderId        注文 ID
     * @param orderNumber    注文番号
     * @param carrier        配送業者
     * @param trackingNumber 送り状番号
     * @return イベント
     */
    public static OrderShipped of(Instant occurredAt, UUID orderId, String orderNumber, Carrier carrier,
                                  String trackingNumber) {
        return new OrderShipped(DomainEvent.newEventId(), occurredAt, orderId, orderNumber, carrier, trackingNumber);
    }

    /**
     * 配送業者
     */
    public enum Carrier {

        /**
         * ヤマト運輸
         */
        YAMATO,

        /**
         * 佐川急便
         */
        SAGAWA,

        /**
         * 日本郵便
         */
        JAPAN_POST,
    }
}

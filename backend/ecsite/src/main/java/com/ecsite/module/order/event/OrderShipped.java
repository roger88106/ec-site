package com.ecsite.module.order.event;

import com.ecsite.common.model.base.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * 注文の出荷。出荷通知メールに要る値を載せる（通知は注文の API を呼ばない）。
 *
 * @param eventId        イベント ID
 * @param occurredAt     発生時刻（出荷日時）
 * @param orderId        注文 ID
 * @param orderNumber    注文番号
 * @param email          注文のメールアドレス（宛先）
 * @param carrier        配送業者
 * @param trackingNumber 送り状番号（ハイフンを除いた数字）
 */
public record OrderShipped(UUID eventId, Instant occurredAt, UUID orderId, String orderNumber, String email,
                           Carrier carrier, String trackingNumber) implements DomainEvent {

    /**
     * イベントを作る。
     *
     * @param occurredAt     発生時刻（出荷日時）
     * @param orderId        注文 ID
     * @param orderNumber    注文番号
     * @param email          注文のメールアドレス
     * @param carrier        配送業者
     * @param trackingNumber 送り状番号
     * @return イベント
     */
    public static OrderShipped of(Instant occurredAt, UUID orderId, String orderNumber, String email, Carrier carrier,
                                  String trackingNumber) {
        return new OrderShipped(UUID.randomUUID(), occurredAt, orderId, orderNumber, email, carrier, trackingNumber);
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

package com.ecsite.module.shipping.event;

import com.ecsite.common.model.base.DomainEvent;
import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * 配送状態の変化（docs/backend/modules/shipping.md「配送状態と順位」）。<br />
 * 出荷を作ったとき（出荷済み）と、通知で配送状態が進んだときに 1 つ出る。反映しなかった通知では出ない。
 * レポートが、注文の写しの配送状態と、状態の履歴に使う。
 *
 * @param eventId    イベント ID
 * @param occurredAt 発生時刻（出荷日時、または通知を反映した日時）
 * @param orderId    注文 ID
 * @param from       前の配送状態。出荷を作ったときは null
 * @param to         後の配送状態
 */
public record DeliveryStatusChanged(UUID eventId, Instant occurredAt, UUID orderId, @Nullable Status from, Status to)
        implements DomainEvent {

    /**
     * イベントを作る。
     *
     * @param occurredAt 発生時刻
     * @param orderId    注文 ID
     * @param from       前の配送状態。出荷を作ったときは null
     * @param to         後の配送状態
     * @return イベント
     */
    public static DeliveryStatusChanged of(Instant occurredAt, UUID orderId, @Nullable Status from, Status to) {
        return new DeliveryStatusChanged(UUID.randomUUID(), occurredAt, orderId, from, to);
    }

    /**
     * 配送状態
     */
    public enum Status {

        /**
         * 出荷済み
         */
        SHIPPED,

        /**
         * 配送中
         */
        IN_TRANSIT,

        /**
         * 配達完了
         */
        DELIVERED,

        /**
         * 返送
         */
        RETURNED,
    }
}

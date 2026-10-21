package com.ecsite.module.order.event;

import com.ecsite.common.model.base.DomainEvent;
import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * 注文の状態の変化（docs/design/modules/order.md「状態遷移」）。<br />
 * 状態が変わるたびに 1 つ出る（作成の「支払待ち」を含む）。状態が変わらない操作では出ない。
 * レポートが、注文の写しを読み直すきっかけと、状態の履歴に使う。
 *
 * @param eventId          イベント ID
 * @param occurredAt       発生時刻（状態が変わった日時）
 * @param orderId          注文 ID
 * @param from             前の状態。作成では null
 * @param to               後の状態
 * @param cancellationKind キャンセルの種類。後の状態がキャンセルでなければ null
 * @param restocked        在庫に戻したか。後の状態がキャンセルでなければ null
 */
public record OrderStatusChanged(UUID eventId, Instant occurredAt, UUID orderId, @Nullable Status from, Status to,
                                 @Nullable CancellationKind cancellationKind, @Nullable Boolean restocked)
        implements DomainEvent {

    /**
     * イベントを作る。
     *
     * @param occurredAt       発生時刻
     * @param orderId          注文 ID
     * @param from             前の状態。作成では null
     * @param to               後の状態
     * @param cancellationKind キャンセルの種類。キャンセルでなければ null
     * @param restocked        在庫に戻したか。キャンセルでなければ null
     * @return イベント
     */
    public static OrderStatusChanged of(Instant occurredAt, UUID orderId, @Nullable Status from, Status to,
                                        @Nullable CancellationKind cancellationKind, @Nullable Boolean restocked) {
        return new OrderStatusChanged(UUID.randomUUID(), occurredAt, orderId, from, to, cancellationKind, restocked);
    }

    /**
     * 注文の状態
     */
    public enum Status {

        /**
         * 支払待ち
         */
        PENDING_PAYMENT,

        /**
         * 受付
         */
        ACCEPTED,

        /**
         * キャンセル申請中
         */
        CANCELLATION_REQUESTED,

        /**
         * キャンセル
         */
        CANCELLED,

        /**
         * 出荷済み
         */
        SHIPPED,
    }

    /**
     * キャンセルの種類
     */
    public enum CancellationKind {

        /**
         * 申請の承認
         */
        REQUEST_APPROVED,

        /**
         * 運営者の判断
         */
        OPERATOR,

        /**
         * 支払期限切れ
         */
        PAYMENT_EXPIRED,
    }
}

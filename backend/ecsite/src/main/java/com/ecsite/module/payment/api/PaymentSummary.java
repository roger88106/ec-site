package com.ecsite.module.payment.api;

import java.time.Instant;
import java.util.UUID;

/**
 * 注文の支払の状態（画面の表示用）。
 *
 * @param orderId  注文 ID
 * @param status   支払の状態
 * @param deadline 支払の期限
 */
public record PaymentSummary(UUID orderId, Status status, Instant deadline) {

    /**
     * 支払の状態（docs/backend/modules/payment.md「状態遷移」）
     */
    public enum Status {

        /**
         * 未払い
         */
        UNPAID,

        /**
         * 与信済み
         */
        AUTHORIZED,

        /**
         * 与信の取消待ち
         */
        VOID_PENDING,

        /**
         * 与信の取消済み
         */
        VOIDED,

        /**
         * 支払なし
         */
        NOT_PAID,

        /**
         * 売上確定済み
         */
        CAPTURED,

        /**
         * 与信期限切れ
         */
        AUTHORIZATION_EXPIRED,

        /**
         * 返金待ち
         */
        REFUND_PENDING,

        /**
         * 返金済み
         */
        REFUNDED,
    }
}

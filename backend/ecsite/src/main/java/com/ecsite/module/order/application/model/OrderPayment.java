package com.ecsite.module.order.application.model;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * 注文の支払の表示
 *
 * @param status                支払の状態
 * @param deadline              支払の期限
 * @param payable               支払画面に移れるか（未払いで、支払の期限の前）
 * @param authorizationDeadline 与信期限。与信を受けていなければ null
 * @param authorizationExpiring 与信期限が近いか（期限を過ぎたものを含む。売上確定の前だけ）
 */
public record OrderPayment(Status status, Instant deadline, boolean payable,
                           @Nullable Instant authorizationDeadline, boolean authorizationExpiring) {

    /**
     * 支払の状態（docs/design/modules/payment.md「状態遷移」）
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

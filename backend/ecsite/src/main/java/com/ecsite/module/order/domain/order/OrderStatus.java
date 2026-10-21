package com.ecsite.module.order.domain.order;

/**
 * 注文の状態
 */
public enum OrderStatus {

    /**
     * 支払待ち。与信の成功で受付に、支払の期限切れでキャンセルになる
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
     * キャンセル。ほかの状態には戻せない
     */
    CANCELLED,

    /**
     * 出荷済み（売上確定の後）。配送状態が返送のときだけ、運営者がキャンセルできる
     */
    SHIPPED,
}

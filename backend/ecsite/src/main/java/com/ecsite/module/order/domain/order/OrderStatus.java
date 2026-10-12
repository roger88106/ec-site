package com.ecsite.module.order.domain.order;

/**
 * 注文の状態
 */
public enum OrderStatus {

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
}

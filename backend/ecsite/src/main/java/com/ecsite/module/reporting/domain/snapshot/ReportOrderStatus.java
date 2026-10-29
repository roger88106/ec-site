package com.ecsite.module.reporting.domain.snapshot;

/**
 * 注文の状態（docs/03-機能仕様/order.md「状態遷移」の写し）
 */
public enum ReportOrderStatus implements HistoryStatus {

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

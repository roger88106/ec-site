package com.ecsite.module.reporting.domain.snapshot;

/**
 * 注文の状態
 *
 * <p>注文モジュールの注文の状態の写し。
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

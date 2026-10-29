package com.ecsite.module.reporting.domain.snapshot;

/**
 * キャンセルの種類（注文のキャンセルの種類の写し。docs/03-機能仕様/order.md）
 */
public enum ReportCancellationKind {

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

package com.ecsite.module.reporting.domain;

/**
 * キャンセルの種類（注文のキャンセルの種類の写し。docs/design/modules/order.md）
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

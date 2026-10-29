package com.ecsite.module.order.domain.order;

/**
 * キャンセルの種類（docs/03-機能仕様/order.md「状態遷移」）
 */
public enum CancellationKind {

    /**
     * 消費者の申請を運営者が承認した
     */
    REQUEST_APPROVED,

    /**
     * 運営者の判断（申請の承認によらない）。理由が必須
     */
    OPERATOR,

    /**
     * 支払の期限までに与信が成功しなかった。運営者と理由を持たない
     */
    PAYMENT_EXPIRED,
}

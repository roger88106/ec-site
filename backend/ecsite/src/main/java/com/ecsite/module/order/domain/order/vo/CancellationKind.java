package com.ecsite.module.order.domain.order.vo;

import com.ecsite.module.order.event.OrderStatusChanged;

/**
 * キャンセルの種類
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
    ;

    /**
     * キャンセルの種類を、イベントの種類にする。
     *
     * @return イベントの種類
     */
    public OrderStatusChanged.CancellationKind toEventKind() {
        return switch (this) {
            case REQUEST_APPROVED -> OrderStatusChanged.CancellationKind.REQUEST_APPROVED;
            case OPERATOR -> OrderStatusChanged.CancellationKind.OPERATOR;
            case PAYMENT_EXPIRED -> OrderStatusChanged.CancellationKind.PAYMENT_EXPIRED;
        };
    }
}

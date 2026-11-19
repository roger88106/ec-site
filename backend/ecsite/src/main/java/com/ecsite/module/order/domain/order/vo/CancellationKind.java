package com.ecsite.module.order.domain.order.vo;

import com.ecsite.common.conversion.EnumConversion;
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
     * 運営者の判断
     *
     * <p>申請の承認によらない。理由が必須。
     */
    OPERATOR,

    /**
     * 支払の期限までに与信が成功しなかった
     *
     * <p>運営者と理由を持たない。
     */
    PAYMENT_EXPIRED,
    ;

    /**
     * キャンセルの種類を、イベントの種類にする。
     *
     * @return イベントの種類
     */
    public OrderStatusChanged.CancellationKind toEventKind() {
        return EnumConversion.byName(this, OrderStatusChanged.CancellationKind.class);
    }
}

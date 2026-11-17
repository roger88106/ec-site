package com.ecsite.module.order.application.model;

import com.ecsite.module.order.application.model.view.OrderPayment;

import java.time.Instant;

/**
 * 決済から受け取った注文の支払
 *
 * @param status   支払の状態
 * @param deadline 支払の期限
 * @param payable  支払えるか（未払いで、支払の期限の前。決済が判定する）
 */
public record PaymentSnapshot(OrderPayment.Status status, Instant deadline, boolean payable) {
}

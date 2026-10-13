package com.ecsite.module.order.application.model;

import com.ecsite.module.order.application.port.PaymentDirectory;

import java.time.Instant;

/**
 * 注文の支払の表示
 *
 * @param status   支払の状態
 * @param deadline 支払の期限
 * @param payable  支払の画面に移れるか（未払いで、支払の期限の前）
 */
public record OrderPayment(PaymentDirectory.Status status, Instant deadline, boolean payable) {
}

package com.ecsite.module.payment.application.model.view;

import com.ecsite.common.model.id.OrderId;
import com.ecsite.module.payment.domain.payment.PaymentStatus;

import java.time.Instant;

/**
 * 注文の支払の状態
 *
 * @param orderId  注文 ID
 * @param status   支払の状態
 * @param deadline 支払の期限
 */
public record PaymentSummaryView(OrderId orderId, PaymentStatus status, Instant deadline) {
}

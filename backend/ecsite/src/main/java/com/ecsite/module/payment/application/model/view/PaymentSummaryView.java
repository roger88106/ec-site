package com.ecsite.module.payment.application.model.view;

import com.ecsite.module.payment.domain.payment.PaymentStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * 注文の支払の状態
 *
 * @param orderId  注文 ID
 * @param status   支払の状態
 * @param deadline 支払の期限
 */
public record PaymentSummaryView(UUID orderId, PaymentStatus status, Instant deadline) {
}

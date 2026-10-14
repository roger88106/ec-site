package com.ecsite.module.payment.application.model.view;

import com.ecsite.module.payment.domain.payment.PaymentStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * 注文の与信の状態
 *
 * @param orderId      注文 ID
 * @param status       支払の状態
 * @param authorizedAt 与信の時刻
 */
public record AuthorizationView(UUID orderId, PaymentStatus status, Instant authorizedAt) {
}

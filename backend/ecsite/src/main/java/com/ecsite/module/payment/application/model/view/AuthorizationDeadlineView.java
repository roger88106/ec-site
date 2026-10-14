package com.ecsite.module.payment.application.model.view;

import java.time.Instant;
import java.util.UUID;

/**
 * 注文の与信期限
 *
 * @param orderId  注文 ID
 * @param deadline 与信期限
 * @param expiring 期限が近い（過ぎたものを含む）なら true
 */
public record AuthorizationDeadlineView(UUID orderId, Instant deadline, boolean expiring) {
}

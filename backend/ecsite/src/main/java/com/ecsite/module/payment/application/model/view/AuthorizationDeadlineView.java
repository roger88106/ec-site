package com.ecsite.module.payment.application.model.view;

import com.ecsite.common.model.id.OrderId;

import java.time.Instant;

/**
 * 注文の与信期限
 *
 * @param orderId  注文 ID
 * @param deadline 与信期限
 * @param expiring 期限が近い（過ぎたものを含む）なら true
 */
public record AuthorizationDeadlineView(OrderId orderId, Instant deadline, boolean expiring) {
}

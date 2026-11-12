package com.ecsite.module.order.application.model;

import java.time.Instant;

/**
 * 注文の与信期限
 *
 * @param deadline 与信期限
 * @param expiring 期限が近いか（期限を過ぎたものを含む。売上確定の前だけ）
 */
public record PaymentAuthorization(Instant deadline, boolean expiring) {
}

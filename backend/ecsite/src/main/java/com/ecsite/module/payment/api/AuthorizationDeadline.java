package com.ecsite.module.payment.api;

import java.time.Instant;
import java.util.UUID;

/**
 * 注文の与信期限（画面の表示用）。
 *
 * @param orderId  注文 ID
 * @param deadline 与信期限。この時刻を過ぎると、売上を確定できない
 * @param expiring 期限が近い（過ぎたものを含む）なら true。売上確定の前の注文だけが対象
 */
public record AuthorizationDeadline(UUID orderId, Instant deadline, boolean expiring) {
}

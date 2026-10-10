package com.ecsite.module.order.application;

import com.ecsite.module.order.domain.quote.Quote;
import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * 注文手続きの表示（AC-85）
 *
 * @param id        注文手続き ID
 * @param quote     表示の時点の見積もり（自分の引当を販売可能数から除く）
 * @param expiresAt 引当の期限
 * @param orderId   注文が成立済みなら、その注文 ID。まだなら null
 */
public record CheckoutView(UUID id, Quote quote, Instant expiresAt, @Nullable UUID orderId) {
}

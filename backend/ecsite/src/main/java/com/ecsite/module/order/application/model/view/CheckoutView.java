package com.ecsite.module.order.application.model.view;

import com.ecsite.common.model.id.OrderId;
import com.ecsite.module.order.domain.checkout.CheckoutId;
import com.ecsite.module.order.domain.quote.Quote;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * 注文手続きの表示
 *
 * @param id        注文手続き ID
 * @param quote     表示の時点の見積もり（自分の引当を販売可能数から除く）
 * @param expiresAt 引当の期限
 * @param orderId   注文が成立済みなら、その注文 ID。まだなら null
 */
public record CheckoutView(CheckoutId id, Quote quote, Instant expiresAt, @Nullable OrderId orderId) {
}

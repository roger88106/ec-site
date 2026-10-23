package com.ecsite.module.order.application.model;

import com.ecsite.module.order.domain.quote.Quote;

import java.util.UUID;

/**
 * カートの見積もりの表示
 *
 * @param cartId カート ID
 * @param quote  見積もり結果
 */
public record CartQuoteView(UUID cartId, Quote quote) {
}

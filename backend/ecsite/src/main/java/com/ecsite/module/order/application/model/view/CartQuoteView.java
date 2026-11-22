package com.ecsite.module.order.application.model.view;

import com.ecsite.common.model.id.CartId;
import com.ecsite.module.order.domain.quote.Quote;

/**
 * カートの見積もりの表示
 *
 * @param cartId カート ID
 * @param quote  見積もり結果
 */
public record CartQuoteView(CartId cartId, Quote quote) {
}

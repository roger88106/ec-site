package com.ecsite.module.order.api;

import com.ecsite.module.order.application.CartQuoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * 店舗に公開する見積もりの API（docs/modules/order.md「他モジュールへの公開」）
 */
@Component
@RequiredArgsConstructor
public class OrderQuoteApi {

    /**
     * カートの見積もりのサービス
     */
    private final CartQuoteService cartQuoteService;

    /**
     * カートを現在の価格・税率・販売可能数で見積もる（AC-77、79〜81）。
     *
     * @param cartId カート ID
     * @return 見積もり。カートがなければ空
     */
    public Optional<CartQuote> quoteCart(UUID cartId) {
        return cartQuoteService.quoteCart(cartId);
    }
}

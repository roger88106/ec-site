package com.ecsite.module.order.api;

import com.ecsite.module.order.application.checkout.CartQuoteService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * 店舗に公開する見積もりの API（docs/design/modules/order.md「他のモジュールへの公開」）
 */
@Component
@RequiredArgsConstructor
public class OrderQuoteApi {

    /**
     * カートの見積もりのサービス
     */
    private final CartQuoteService cartQuoteService;

    /**
     * カートを現在の価格・税率・販売可能数で見積もる。
     *
     * 会員のカートは、持ち主の会員にだけ見積もる。
     *
     * @param cartId    カート ID
     * @param requester 要求した会員の ID（ログインしていなければ null）
     * @return 見積もり。カートがない、または持ち主でなければ空
     */
    public Optional<CartQuote> quoteCart(UUID cartId, @Nullable UUID requester) {
        return cartQuoteService.quoteCart(cartId, requester);
    }
}

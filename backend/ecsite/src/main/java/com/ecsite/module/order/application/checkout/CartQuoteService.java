package com.ecsite.module.order.application.checkout;

import com.ecsite.module.order.application.model.CartQuoteView;
import com.ecsite.module.order.application.port.CartDirectory;
import com.ecsite.module.order.domain.checkout.port.CheckoutRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * カートの見積もりのサービス（店舗のカートの表示）
 */
@Service
@RequiredArgsConstructor
public class CartQuoteService {

    /**
     * カートの腐敗防止層
     */
    private final CartDirectory cartDirectory;

    /**
     * 見積もりのサービス
     */
    private final QuoteService quoteService;

    /**
     * 注文手続きリポジトリ（自分の引当の持ち主）
     */
    private final CheckoutRepository checkoutRepository;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * カートを現在の価格・税率・販売可能数で見積もる。注文手続き中なら、自分の引当を販売可能数から除く。
     *
     * 会員のカートは、持ち主の会員にだけ見積もる（ほかの人には、カートがないときと同じく空）。
     *
     * @param cartId    カート ID
     * @param requester 要求した会員の ID（ログインしていなければ null）
     * @return 見積もり。カートがない、または持ち主でなければ空
     */
    @Transactional(readOnly = true)
    public Optional<CartQuoteView> quoteCart(UUID cartId, @Nullable UUID requester) {
        var now = clock.instant();
        var holderId = checkoutRepository.findLatestIdByCartId(cartId).orElse(null);
        return cartDirectory.find(cartId)
                .filter(cart -> cart.isAccessibleBy(requester))
                .map(cart -> new CartQuoteView(cartId, quoteService.quote(cart.lines(), now, holderId)));
    }
}

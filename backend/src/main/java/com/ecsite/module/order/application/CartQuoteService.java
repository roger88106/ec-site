package com.ecsite.module.order.application;

import com.ecsite.module.order.api.CartQuote;
import com.ecsite.module.order.domain.checkout.port.CheckoutRepository;
import com.ecsite.module.order.domain.port.CartDirectory;
import com.ecsite.module.order.domain.quote.Quote;
import com.ecsite.module.order.domain.quote.QuoteItem;
import com.ecsite.module.order.domain.quote.TaxAmount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * カートの見積もりのサービス（店舗のカートの表示。AC-77、79〜81）
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
     * @param cartId カート ID
     * @return 見積もり。カートがなければ空
     */
    @Transactional(readOnly = true)
    public Optional<CartQuote> quoteCart(UUID cartId) {
        var now = clock.instant();
        var holderId = checkoutRepository.findLatestIdByCartId(cartId).orElse(null);
        return cartDirectory.find(cartId).map(cart -> toCartQuote(cartId, quoteService.quote(cart.lines(), now, holderId)));
    }

    /**
     * 見積もりを、公開する型に変換する。
     */
    static CartQuote toCartQuote(UUID cartId, Quote quote) {
        var amounts = quote.amounts();
        return new CartQuote(cartId,
                quote.items().stream().map(CartQuoteService::toItem).toList(),
                new CartQuote.Amounts(amounts.itemsTotal(), amounts.shippingFee(), amounts.total(),
                        amounts.taxes().stream().map(CartQuoteService::toTaxAmount).toList()),
                quote.orderable());
    }

    /**
     * 見積もりの品目を、公開する型に変換する。
     */
    private static CartQuote.Item toItem(QuoteItem item) {
        var variant = item.variant();
        return new CartQuote.Item(variant.variantId(), variant.productId(), variant.productName(),
                variant.axisValues().stream().map(axis -> new CartQuote.AxisValue(axis.name(), axis.value())).toList(),
                variant.imageUrl(), variant.priceExclTax(), item.unitPriceInclTax(), item.quantity(), item.subtotal(),
                item.purchasable() ? null : item.unpurchasableReason().name());
    }

    /**
     * 消費税額を、公開する型に変換する。
     */
    private static CartQuote.TaxAmount toTaxAmount(TaxAmount tax) {
        return new CartQuote.TaxAmount(tax.ratePercent(), tax.taxableAmount(), tax.taxAmount());
    }
}

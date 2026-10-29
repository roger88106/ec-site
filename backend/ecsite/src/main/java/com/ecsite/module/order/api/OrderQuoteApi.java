package com.ecsite.module.order.api;

import com.ecsite.module.order.application.checkout.CartQuoteService;
import com.ecsite.module.order.application.model.CartQuoteView;
import com.ecsite.module.order.domain.quote.Amounts;
import com.ecsite.module.order.domain.quote.QuoteItem;
import com.ecsite.module.order.domain.quote.TaxAmount;
import com.ecsite.module.order.domain.quote.UnpurchasableReason;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * 店舗に公開する見積もりの API（docs/03-機能仕様/order.md「他のモジュールへの公開」）
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
        return cartQuoteService.quoteCart(cartId, requester).map(OrderQuoteApi::toCartQuote);
    }

    /**
     * 見積もりの表示を、公開する型に変換する。
     */
    static CartQuote toCartQuote(CartQuoteView view) {
        var quote = view.quote();
        return new CartQuote(
                view.cartId(),
                quote.items().stream().map(OrderQuoteApi::toItem).toList(),
                toAmounts(quote.amounts()),
                quote.orderable());
    }

    /**
     * 見積もりの品目を、公開する型に変換する。
     */
    private static CartQuote.Item toItem(QuoteItem item) {
        var variant = item.variant();
        var reason = item.unpurchasableReason();
        return new CartQuote.Item(
                variant.variantId(),
                variant.productId(),
                variant.productName(),
                variant.axisValues().stream().map(axis -> new CartQuote.AxisValue(axis.name(), axis.value())).toList(),
                variant.imageUrl(),
                variant.priceExclTax(),
                item.unitPriceInclTax(),
                item.quantity(),
                item.subtotal(),
                reason == null ? null : toReason(reason));
    }

    /**
     * 購入できない理由を、公開する型に変換する。
     */
    static CartQuote.UnpurchasableReason toReason(UnpurchasableReason reason) {
        return switch (reason) {
            case NOT_SHOWN -> CartQuote.UnpurchasableReason.NOT_SHOWN;
            case SALES_SUSPENDED -> CartQuote.UnpurchasableReason.SALES_SUSPENDED;
            case OUT_OF_STOCK -> CartQuote.UnpurchasableReason.OUT_OF_STOCK;
            case INSUFFICIENT_STOCK -> CartQuote.UnpurchasableReason.INSUFFICIENT_STOCK;
        };
    }

    /**
     * 金額の内訳を、公開する型に変換する。
     */
    private static CartQuote.Amounts toAmounts(Amounts amounts) {
        return new CartQuote.Amounts(
                amounts.itemsTotal(),
                amounts.shippingFee(),
                amounts.total(),
                amounts.taxes().stream().map(OrderQuoteApi::toTaxAmount).toList());
    }

    /**
     * 消費税額を、公開する型に変換する。
     */
    private static CartQuote.TaxAmount toTaxAmount(TaxAmount tax) {
        return new CartQuote.TaxAmount(tax.ratePercent(), tax.taxableAmount(), tax.taxAmount());
    }
}

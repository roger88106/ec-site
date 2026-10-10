package com.ecsite.module.storefront.orderdir;

import com.ecsite.module.order.api.CartQuote;
import com.ecsite.module.order.api.OrderQuoteApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * 注文の腐敗防止層（constitution 4.1、docs/modules/storefront.md）。<br />
 * 注文モジュールの見積もりの API を呼び、カートの表示に変換する。
 */
@Component
@RequiredArgsConstructor
public class OrderDirectory {

    /**
     * 見積もりの API
     */
    private final OrderQuoteApi orderQuoteApi;

    /**
     * カートを見積もり、表示の形にする。
     *
     * @param cartId カート ID
     * @return カートの表示。カートがなければ空
     */
    public Optional<CartView> findCart(UUID cartId) {
        return orderQuoteApi.quoteCart(cartId).map(OrderDirectory::toView);
    }

    /**
     * 注文モジュールの型を、店舗モジュールの型に変換する。
     */
    private static CartView toView(CartQuote quote) {
        var amounts = quote.amounts();
        return new CartView(quote.cartId(),
                quote.items().stream().map(item -> new CartView.Item(item.variantId(), item.productId(),
                        item.productName(),
                        item.axisValues().stream().map(axis -> new CartView.AxisValue(axis.name(), axis.value())).toList(),
                        item.imageUrl(), item.unitPriceExclTax(), item.unitPriceInclTax(), item.quantity(), item.subtotal(),
                        item.unpurchasableReason())).toList(),
                new CartView.Amounts(amounts.itemsTotal(), amounts.shippingFee(), amounts.total(),
                        amounts.taxes().stream()
                                .map(tax -> new CartView.TaxAmount(tax.ratePercent(), tax.taxableAmount(), tax.taxAmount()))
                                .toList()),
                quote.orderable());
    }
}

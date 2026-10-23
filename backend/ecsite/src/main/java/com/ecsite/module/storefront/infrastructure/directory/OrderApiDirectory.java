package com.ecsite.module.storefront.infrastructure.directory;

import com.ecsite.module.order.api.CartQuote;
import com.ecsite.module.order.api.OrderQuoteApi;
import com.ecsite.module.storefront.application.model.CartView;
import com.ecsite.module.storefront.application.port.OrderDirectory;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * 注文の腐敗防止層（バックエンドのアーキテクチャ 第 1 節、docs/design/modules/storefront.md）。<br />
 * 注文モジュールの見積もりの API を呼び、カートの表示に変換する。
 */
@Component
@RequiredArgsConstructor
class OrderApiDirectory implements OrderDirectory {

    /**
     * 見積もりの API
     */
    private final OrderQuoteApi orderQuoteApi;

    /**
     * カートを見積もり、表示の形にする。
     *
     * @param cartId    カート ID
     * @param requester 要求した会員の ID（ログインしていなければ null）
     * @return カートの表示。カートがない、または会員のカートで持ち主でなければ空
     */
    @Override
    public Optional<CartView> findCart(UUID cartId, @Nullable UUID requester) {
        return orderQuoteApi.quoteCart(cartId, requester).map(OrderApiDirectory::toView);
    }

    /**
     * 注文モジュールの型を、店舗モジュールの型に変換する。
     */
    private static CartView toView(CartQuote quote) {
        return new CartView(
                quote.cartId(),
                quote.items().stream().map(OrderApiDirectory::toItem).toList(),
                toAmounts(quote.amounts()),
                quote.orderable());
    }

    /**
     * 品目を変換する。
     */
    private static CartView.Item toItem(CartQuote.Item item) {
        return new CartView.Item(
                item.variantId(),
                item.productId(),
                item.productName(),
                item.axisValues().stream().map(axis -> new CartView.AxisValue(axis.name(), axis.value())).toList(),
                item.imageUrl(),
                item.unitPriceExclTax(),
                item.unitPriceInclTax(),
                item.quantity(),
                item.subtotal(),
                item.unpurchasableReason() == null ? null : toReason(item.unpurchasableReason()));
    }

    /**
     * 購入できない理由を変換する。
     */
    static CartView.UnpurchasableReason toReason(CartQuote.UnpurchasableReason reason) {
        return switch (reason) {
            case NOT_SHOWN -> CartView.UnpurchasableReason.NOT_SHOWN;
            case SALES_SUSPENDED -> CartView.UnpurchasableReason.SALES_SUSPENDED;
            case OUT_OF_STOCK -> CartView.UnpurchasableReason.OUT_OF_STOCK;
            case INSUFFICIENT_STOCK -> CartView.UnpurchasableReason.INSUFFICIENT_STOCK;
        };
    }

    /**
     * 金額の内訳を変換する。
     */
    private static CartView.Amounts toAmounts(CartQuote.Amounts amounts) {
        return new CartView.Amounts(
                amounts.itemsTotal(),
                amounts.shippingFee(),
                amounts.total(),
                amounts.taxes().stream()
                        .map(tax -> new CartView.TaxAmount(tax.ratePercent(), tax.taxableAmount(), tax.taxAmount()))
                        .toList());
    }
}

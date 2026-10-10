package com.ecsite.module.order.presentation;

import com.ecsite.api.store.model.Amounts;
import com.ecsite.api.store.model.AxisValue;
import com.ecsite.api.store.model.Checkout;
import com.ecsite.api.store.model.Price;
import com.ecsite.api.store.model.QuoteItem;
import com.ecsite.api.store.model.TaxAmount;
import com.ecsite.api.store.model.UnpurchasableReason;
import com.ecsite.module.order.application.CheckoutView;
import com.ecsite.module.order.domain.quote.Quote;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.List;

/**
 * 店舗 API（注文手続き・注文）のレスポンスと、アプリケーション層のモデルの変換
 */
@Component
class StoreOrderMapper {

    /**
     * 注文手続きのレスポンスに変換する（AC-85）。
     *
     * @param view 注文手続きの表示
     * @return 注文手続きのレスポンス
     */
    Checkout toCheckout(CheckoutView view) {
        return new Checkout(view.id(), toQuoteItems(view.quote()), toAmounts(view.quote().amounts()),
                view.expiresAt().atOffset(ZoneOffset.UTC))
                .orderId(view.orderId());
    }

    /**
     * 見積もりの品目のレスポンスに変換する。画像と購入できない理由は、null でも項目として返す。
     *
     * @param quote 見積もり
     * @return 品目のレスポンス
     */
    List<QuoteItem> toQuoteItems(Quote quote) {
        return quote.items().stream().map(item -> {
            var variant = item.variant();
            return new QuoteItem(variant.variantId(), variant.productId(), variant.productName(),
                    variant.axisValues().stream().map(axis -> new AxisValue(axis.name(), axis.value())).toList(),
                    new Price(item.unitPriceInclTax().yen(), variant.priceExclTax().yen()), item.quantity(),
                    item.subtotal().yen(), item.purchasable())
                    .imageUrl(variant.imageUrl())
                    .unpurchasableReason(item.purchasable()
                            ? null : UnpurchasableReason.fromValue(item.unpurchasableReason().name()));
        }).toList();
    }

    /**
     * 金額の内訳のレスポンスに変換する（BR-7、8）。
     *
     * @param amounts 金額の内訳
     * @return 金額の内訳のレスポンス
     */
    Amounts toAmounts(com.ecsite.module.order.domain.quote.Amounts amounts) {
        return new Amounts(amounts.itemsTotal().yen(), amounts.shippingFee().yen(), amounts.total().yen(),
                amounts.taxes().stream()
                        .map(tax -> new TaxAmount(tax.ratePercent(), tax.taxableAmount().yen(), tax.taxAmount().yen()))
                        .toList());
    }
}

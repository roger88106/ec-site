package com.ecsite.module.order.domain.quote;

import com.ecsite.common.model.Money;
import com.ecsite.module.order.domain.quote.port.TaxDirectory;
import lombok.NonNull;
import org.jmolecules.ddd.annotation.ValueObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 見積もり（docs/modules/order.md「見積もり」）。カートの表示、注文手続き、注文の成立で同じ計算をする（BR-7）。
 *
 * @param items     品目（カートの並び）
 * @param amounts   金額の内訳
 * @param orderable 注文できるか（品目が 1 つ以上あり、すべて購入できる。AC-88）
 */
@ValueObject
public record Quote(List<QuoteItem> items, Amounts amounts, boolean orderable) {

    /**
     * 品目から金額の内訳を求める。購入できない品目は合計・消費税額に含めない（AC-80）。<br />
     * 送料は購入できる品目があるときだけかけ、標準税率の対象として消費税額に含める（BR-6、8）。
     *
     * @param items          品目
     * @param missingVariant 存在しなくなったバリアントがカートにあるか（あれば注文できない）
     * @param shipping       送料
     * @param tax            税の腐敗防止層（消費税額の計算）
     * @return 見積もり
     */
    public static Quote of(@NonNull List<QuoteItem> items, boolean missingVariant, @NonNull Shipping shipping,
                           @NonNull TaxDirectory tax) {
        var purchasable = items.stream().filter(QuoteItem::purchasable).toList();
        var itemsTotal = purchasable.stream().map(QuoteItem::subtotal).reduce(Money.ZERO, Money::plus);
        var shippingFee = purchasable.isEmpty() ? Money.ZERO : shipping.fee();

        var taxables = new ArrayList<TaxDirectory.Taxable>();
        purchasable.forEach(item -> taxables.add(new TaxDirectory.Taxable(item.ratePercent(), item.subtotal())));
        if (shippingFee.yen() > 0) {
            taxables.add(new TaxDirectory.Taxable(shipping.ratePercent(), shippingFee));
        }
        var taxes = taxables.isEmpty() ? List.<TaxAmount>of() : tax.consumptionTaxes(taxables);

        var amounts = new Amounts(itemsTotal, shippingFee, itemsTotal.plus(shippingFee), taxes);
        var orderable = !items.isEmpty() && !missingVariant && purchasable.size() == items.size();
        return new Quote(List.copyOf(items), amounts, orderable);
    }

    /**
     * 購入できない品目があり、その理由がすべて在庫によるものか（注文手続き・注文の拒否を 409 にする）。
     *
     * @return 在庫だけが足りないなら true
     */
    public boolean shortOfStockOnly() {
        var unpurchasable = items.stream().filter(item -> !item.purchasable()).toList();
        return !unpurchasable.isEmpty()
                && unpurchasable.stream().allMatch(item -> item.unpurchasableReason().isStockShortage());
    }
}

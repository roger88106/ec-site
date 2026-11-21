package com.ecsite.module.order.domain.quote;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.order.domain.quote.port.TaxDirectory;
import lombok.NonNull;
import org.jmolecules.ddd.annotation.ValueObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 見積もり
 *
 * @param items       品目（カートの並び）
 * @param amounts     金額の内訳
 * @param shippingTax 送料の消費税（送料だけを課税対象として求めた額。送料が 0 なら 0）
 * @param orderable   注文できるか（品目が 1 つ以上あり、すべて購入できる）
 */
@ValueObject
public record Quote(List<QuoteItem> items, Amounts amounts, Money shippingTax, boolean orderable) {

    /**
     * 品目から金額の内訳を求める。
     *
     * <p>購入できない品目は合計・消費税額に含めない。
     * 送料は購入できる品目があるときだけかけ、標準税率の対象として消費税額に含める。
     * 送料の消費税は、それとは別に、送料だけを課税対象として求める。
     *
     * @param items          品目
     * @param missingVariant 存在しなくなったバリアントがカートにあるか（あれば注文できない）
     * @param shipping       送料
     * @param tax            税の腐敗防止層（消費税額の計算）
     * @return 見積もり
     */
    public static Quote of(@NonNull List<QuoteItem> items, boolean missingVariant, @NonNull Shipping shipping,
                           @NonNull TaxDirectory tax) {
        // 買える品目だけを合計する。送料は、買える品目があるときだけかかる
        var purchasable = items.stream().filter(QuoteItem::purchasable).toList();
        var itemsTotal = purchasable.stream().map(QuoteItem::subtotal).reduce(Money.ZERO, Money::plus);
        var shippingFee = purchasable.isEmpty() ? Money.ZERO : shipping.fee();

        // 消費税は、品目と送料（あれば）の課税対象から、税率ごとに求める
        var taxables = new ArrayList<TaxDirectory.Taxable>();
        purchasable.forEach(item -> taxables.add(new TaxDirectory.Taxable(
                Objects.requireNonNull(item.ratePercent(), "買える品目には率がある"), item.subtotal())));
        if (shippingFee.yen() > 0) {
            taxables.add(new TaxDirectory.Taxable(shipping.ratePercent(), shippingFee));
        }
        var taxes = taxables.isEmpty() ? List.<TaxAmount>of() : tax.consumptionTaxes(taxables);

        var amounts = new Amounts(itemsTotal, shippingFee, itemsTotal.plus(shippingFee), taxes);
        // 品目があり、なくなったバリアントがなく、すべての品目が買えるときだけ注文できる
        var orderable = !items.isEmpty() && !missingVariant && purchasable.size() == items.size();
        return new Quote(List.copyOf(items), amounts, shippingTax(shippingFee, shipping.ratePercent(), tax), orderable);
    }

    /**
     * 送料の消費税を求める。
     *
     * <p>送料が 0 なら 0。税率ごとの消費税額は品目と送料を合算して求めるので、そこから送料の分は取り出せない。
     * そのため、送料だけを課税対象として求める。
     *
     * @param shippingFee 送料（税込）
     * @param ratePercent 送料に適用する率（%）
     * @param tax         税の腐敗防止層（消費税額の計算）
     * @return 送料の消費税
     */
    private static Money shippingTax(Money shippingFee, BigDecimal ratePercent, TaxDirectory tax) {
        if (shippingFee.yen() == 0) {
            return Money.ZERO;
        }
        return tax.consumptionTaxes(List.of(new TaxDirectory.Taxable(ratePercent, shippingFee))).stream()
                .map(TaxAmount::taxAmount)
                .reduce(Money.ZERO, Money::plus);
    }

    /**
     * 購入できない品目があり、その理由がすべて在庫によるものか。
     *
     * @return 在庫だけが足りないなら true
     */
    public boolean shortOfStockOnly() {
        var reasons = items.stream().map(QuoteItem::unpurchasableReason).filter(Objects::nonNull).toList();
        return !reasons.isEmpty() && reasons.stream().allMatch(UnpurchasableReason::isStockShortage);
    }
}

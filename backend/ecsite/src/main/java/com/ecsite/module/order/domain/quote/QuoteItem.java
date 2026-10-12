package com.ecsite.module.order.domain.quote;

import com.ecsite.common.model.value.Money;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import org.jmolecules.ddd.annotation.ValueObject;

import java.math.BigDecimal;

/**
 * 見積もりの品目（BR-7、AC-80）
 *
 * @param variant             バリアントの情報
 * @param quantity            数量
 * @param ratePercent         時点の率（%）。有効な税率がなければ null
 * @param unitPriceInclTax    税込単価（有効な税率がなければ税抜価格）
 * @param subtotal            小計（税込単価 × 数量）
 * @param unpurchasableReason 購入できない理由。購入できれば null
 */
@ValueObject
public record QuoteItem(VariantInfo variant, int quantity, @Nullable BigDecimal ratePercent, Money unitPriceInclTax,
                        Money subtotal, @Nullable UnpurchasableReason unpurchasableReason) {

    /**
     * 品目を作り、購入できるかを判定する。
     *
     * @param variant          バリアントの情報
     * @param quantity         数量
     * @param sellable         販売可能数（自分の引当を除いた数）
     * @param ratePercent      時点の率。有効な税率がなければ null
     * @param unitPriceInclTax 税込単価。有効な税率がなければ null
     * @return 品目
     */
    public static QuoteItem of(@NonNull VariantInfo variant, int quantity, int sellable,
                               @Nullable BigDecimal ratePercent, @Nullable Money unitPriceInclTax) {
        var unitPrice = unitPriceInclTax == null ? variant.priceExclTax() : unitPriceInclTax;
        var subtotal = Money.of(Math.multiplyExact(unitPrice.yen(), quantity));
        return new QuoteItem(variant, quantity, ratePercent, unitPrice, subtotal,
                reasonOf(variant, quantity, sellable, ratePercent));
    }

    /**
     * 購入できるか。
     *
     * @return 購入できれば true
     */
    public boolean purchasable() {
        return unpurchasableReason == null;
    }

    /**
     * 購入できない理由を、表示 → 販売停止 → 在庫の順に判定する（AC-80）。
     *
     * @return 理由。購入できれば null
     */
    private static UnpurchasableReason reasonOf(VariantInfo variant, int quantity, int sellable, BigDecimal ratePercent) {
        if (!variant.visible() || ratePercent == null) {
            return UnpurchasableReason.NOT_SHOWN;
        }
        if (variant.salesSuspended()) {
            return UnpurchasableReason.SALES_SUSPENDED;
        }
        if (sellable <= 0) {
            return UnpurchasableReason.OUT_OF_STOCK;
        }
        if (sellable < quantity) {
            return UnpurchasableReason.INSUFFICIENT_STOCK;
        }
        return null;
    }
}

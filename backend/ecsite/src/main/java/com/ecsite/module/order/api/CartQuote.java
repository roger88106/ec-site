package com.ecsite.module.order.api;

import com.ecsite.common.model.value.Money;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * カートの見積もり（docs/design/modules/order.md「他のモジュールへの公開」）。店舗モジュールがカートの表示に使う。
 *
 * @param cartId    カート ID
 * @param items     品目（入れた順）
 * @param amounts   金額の内訳
 * @param orderable 注文できるか（品目が 1 つ以上あり、すべて購入できる）
 */
public record CartQuote(UUID cartId, List<Item> items, Amounts amounts, boolean orderable) {

    /**
     * 品目
     *
     * @param variantId           バリアント ID
     * @param productId           商品 ID
     * @param productName         商品名
     * @param axisValues          軸の名前と値
     * @param imageUrl            画像の URL。なければ null
     * @param unitPriceExclTax    税抜単価
     * @param unitPriceInclTax    税込単価
     * @param quantity            数量
     * @param subtotal            小計
     * @param unpurchasableReason 購入できない理由。購入できれば null
     */
    public record Item(UUID variantId, UUID productId, String productName, List<AxisValue> axisValues,
                       @Nullable String imageUrl, Money unitPriceExclTax, Money unitPriceInclTax, int quantity,
                       Money subtotal, @Nullable UnpurchasableReason unpurchasableReason) {
    }

    /**
     * 軸の名前と値
     *
     * @param name  軸の名前
     * @param value 軸の値
     */
    public record AxisValue(String name, String value) {
    }

    /**
     * 金額の内訳
     *
     * @param itemsTotal  商品の合計
     * @param shippingFee 送料
     * @param total       総額
     * @param taxes       税率ごとの消費税額（税率の高い順）
     */
    public record Amounts(Money itemsTotal, Money shippingFee, Money total, List<TaxAmount> taxes) {
    }

    /**
     * 1 つの税率の消費税額
     *
     * @param ratePercent   率（%）
     * @param taxableAmount 対象額（税込）
     * @param taxAmount     消費税額
     */
    public record TaxAmount(BigDecimal ratePercent, Money taxableAmount, Money taxAmount) {
    }

    /**
     * 購入できない理由
     */
    public enum UnpurchasableReason {

        /**
         * 商品が C 側に表示されない。有効な税率がない場合も含む
         */
        NOT_SHOWN,

        /**
         * バリアントが販売停止
         */
        SALES_SUSPENDED,

        /**
         * 販売可能数がない
         */
        OUT_OF_STOCK,

        /**
         * 販売可能数が数量より少ない
         */
        INSUFFICIENT_STOCK,
    }
}

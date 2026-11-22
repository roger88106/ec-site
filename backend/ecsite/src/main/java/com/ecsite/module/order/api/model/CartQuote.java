package com.ecsite.module.order.api.model;

import com.ecsite.common.model.id.CartId;
import com.ecsite.common.model.id.ProductId;
import com.ecsite.common.model.id.VariantId;
import com.ecsite.common.model.value.Money;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * カートの見積もり
 *
 * @param cartId    カート ID
 * @param items     品目（入れた順）
 * @param amounts   金額の内訳
 * @param orderable 注文できるか（品目が 1 つ以上あり、すべて購入できる）
 */
public record CartQuote(CartId cartId, List<Item> items, Amounts amounts, boolean orderable) {

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
    public record Item(VariantId variantId, ProductId productId, String productName, List<AxisValue> axisValues,
                       @Nullable String imageUrl, Money unitPriceExclTax, Money unitPriceInclTax, int quantity,
                       Money subtotal, @Nullable UnpurchasableReason unpurchasableReason) {
    }

    /**
     * 購入できない理由
     */
    public enum UnpurchasableReason {

        /**
         * 商品が C 側に表示されない
         *
         * <p>有効な税率がない場合も含む。
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

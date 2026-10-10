package com.ecsite.module.cart.service;

import com.ecsite.common.message.I18nMessage;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * カートのメッセージ
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CartMessage {

    /**
     * 1 品目の数量は 1〜10 にしてください。
     */
    public static final I18nMessage INVALID_CART_QUANTITY = I18nMessage.of(Code.INVALID_CART_QUANTITY);

    /**
     * カートに入れられるのは 30 品目までです。
     */
    public static final I18nMessage TOO_MANY_CART_ITEMS = I18nMessage.of(Code.TOO_MANY_CART_ITEMS);

    /**
     * この商品は現在お選びいただけません。
     */
    public static final I18nMessage VARIANT_NOT_SELECTABLE = I18nMessage.of(Code.VARIANT_NOT_SELECTABLE);

    /**
     * 在庫が足りないため、この数量ではカートに入れられません。
     */
    public static final I18nMessage QUANTITY_EXCEEDS_STOCK = I18nMessage.of(Code.QUANTITY_EXCEEDS_STOCK);

    // ============================== コード定義 ==============================

    /**
     * メッセージコード
     */
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Code {

        /**
         * 1 品目の数量は 1〜10 にしてください。
         */
        public static final String INVALID_CART_QUANTITY = "ecsite.cart.invalid_cart_quantity";

        /**
         * カートに入れられるのは 30 品目までです。
         */
        public static final String TOO_MANY_CART_ITEMS = "ecsite.cart.too_many_cart_items";

        /**
         * この商品は現在お選びいただけません。
         */
        public static final String VARIANT_NOT_SELECTABLE = "ecsite.cart.variant_not_selectable";

        /**
         * 在庫が足りないため、この数量ではカートに入れられません。
         */
        public static final String QUANTITY_EXCEEDS_STOCK = "ecsite.cart.quantity_exceeds_stock";
    }
}

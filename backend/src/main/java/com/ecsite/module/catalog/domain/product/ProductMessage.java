package com.ecsite.module.catalog.domain.product;

import com.ecsite.common.message.I18nMessage;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 商品のメッセージ
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ProductMessage {

    /**
     * 商品品番は、半角の英大文字・数字とハイフンで 1〜32 文字にしてください。ハイフンは英数字の間にだけ置けます。
     */
    public static final I18nMessage INVALID_PRODUCT_CODE = I18nMessage.of(Code.INVALID_PRODUCT_CODE);

    /**
     * バリアント品番は、半角の英大文字・数字とハイフンで 1〜32 文字にしてください。ハイフンは英数字の間にだけ置けます。
     */
    public static final I18nMessage INVALID_VARIANT_CODE = I18nMessage.of(Code.INVALID_VARIANT_CODE);

    /**
     * 税抜価格は 1 円以上にしてください。
     */
    public static final I18nMessage INVALID_PRICE_EXCL_TAX = I18nMessage.of(Code.INVALID_PRICE_EXCL_TAX);

    // ============================== コード定義 ==============================

    /**
     * メッセージコード
     */
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Code {
        /**
         * 商品品番は、半角の英大文字・数字とハイフンで 1〜32 文字にしてください。ハイフンは英数字の間にだけ置けます。
         */
        public static final String INVALID_PRODUCT_CODE = "ecsite.catalog.invalid_product_code";

        /**
         * バリアント品番は、半角の英大文字・数字とハイフンで 1〜32 文字にしてください。ハイフンは英数字の間にだけ置けます。
         */
        public static final String INVALID_VARIANT_CODE = "ecsite.catalog.invalid_variant_code";

        /**
         * 税抜価格は 1 円以上にしてください。
         */
        public static final String INVALID_PRICE_EXCL_TAX = "ecsite.catalog.invalid_price_excl_tax";
    }
}

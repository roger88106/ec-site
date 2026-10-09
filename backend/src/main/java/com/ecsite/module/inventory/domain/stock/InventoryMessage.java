package com.ecsite.module.inventory.domain.stock;

import com.ecsite.common.message.I18nMessage;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 在庫モジュールのメッセージ
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class InventoryMessage {

    /**
     * 増減量は 0 以外にしてください。
     */
    public static final I18nMessage INVALID_STOCK_DELTA = I18nMessage.of(Code.INVALID_STOCK_DELTA);

    /**
     * 理由が「その他」のときは、メモを入力してください。
     */
    public static final I18nMessage STOCK_MEMO_REQUIRED = I18nMessage.of(Code.STOCK_MEMO_REQUIRED);

    /**
     * メモは 500 文字以内にしてください。
     */
    public static final I18nMessage INVALID_STOCK_MEMO = I18nMessage.of(Code.INVALID_STOCK_MEMO);

    /**
     * 在庫数が足りません。調整後の在庫数が負になります。
     */
    public static final I18nMessage INSUFFICIENT_STOCK = I18nMessage.of(Code.INSUFFICIENT_STOCK);

    // ============================== コード定義 ==============================

    /**
     * メッセージコード
     */
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Code {
        /**
         * 増減量は 0 以外にしてください。
         */
        public static final String INVALID_STOCK_DELTA = "ecsite.inventory.invalid_stock_delta";

        /**
         * 理由が「その他」のときは、メモを入力してください。
         */
        public static final String STOCK_MEMO_REQUIRED = "ecsite.inventory.stock_memo_required";

        /**
         * メモは 500 文字以内にしてください。
         */
        public static final String INVALID_STOCK_MEMO = "ecsite.inventory.invalid_stock_memo";

        /**
         * 在庫数が足りません。調整後の在庫数が負になります。
         */
        public static final String INSUFFICIENT_STOCK = "ecsite.inventory.insufficient_stock";
    }
}

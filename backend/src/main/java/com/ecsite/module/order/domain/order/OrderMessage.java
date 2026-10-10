package com.ecsite.module.order.domain.order;

import com.ecsite.common.message.I18nMessage;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 注文のメッセージ
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OrderMessage {

    /**
     * メールアドレスの形が正しくありません。
     */
    public static final I18nMessage INVALID_EMAIL = I18nMessage.of(Code.INVALID_EMAIL);

    /**
     * 氏名を 50 文字以内で入力してください。
     */
    public static final I18nMessage INVALID_RECIPIENT_NAME = I18nMessage.of(Code.INVALID_RECIPIENT_NAME);

    /**
     * 郵便番号は数字 7 桁で入力してください。
     */
    public static final I18nMessage INVALID_POSTAL_CODE = I18nMessage.of(Code.INVALID_POSTAL_CODE);

    /**
     * 都道府県を選んでください。
     */
    public static final I18nMessage INVALID_PREFECTURE = I18nMessage.of(Code.INVALID_PREFECTURE);

    /**
     * 住所を 200 文字以内で入力してください。
     */
    public static final I18nMessage INVALID_ADDRESS = I18nMessage.of(Code.INVALID_ADDRESS);

    /**
     * 電話番号は数字 10〜11 桁で入力してください。
     */
    public static final I18nMessage INVALID_PHONE_NUMBER = I18nMessage.of(Code.INVALID_PHONE_NUMBER);

    /**
     * カートが空か、購入できない商品が含まれているため、注文できません。
     */
    public static final I18nMessage CART_NOT_ORDERABLE = I18nMessage.of(Code.CART_NOT_ORDERABLE);

    /**
     * 在庫が足りない商品があります。カートを確かめてください。
     */
    public static final I18nMessage OUT_OF_STOCK = I18nMessage.of(Code.OUT_OF_STOCK);

    /**
     * 価格または税率が変わったため、総額が変わりました。内容を確かめて、もう一度注文してください。
     */
    public static final I18nMessage TOTAL_AMOUNT_CHANGED = I18nMessage.of(Code.TOTAL_AMOUNT_CHANGED);

    /**
     * 注文手続きを始めた後にカートが変わりました。もう一度注文手続きに進んでください。
     */
    public static final I18nMessage CART_CHANGED = I18nMessage.of(Code.CART_CHANGED);

    /**
     * この注文はキャンセルを申請できません。キャンセルをご希望の場合は、お問い合わせください。
     */
    public static final I18nMessage CANCELLATION_NOT_REQUESTABLE = I18nMessage.of(Code.CANCELLATION_NOT_REQUESTABLE);

    /**
     * この注文には、承認・却下できるキャンセルの申請がありません。
     */
    public static final I18nMessage CANCELLATION_NOT_PENDING = I18nMessage.of(Code.CANCELLATION_NOT_PENDING);

    /**
     * キャンセルの理由を入力してください。
     */
    public static final I18nMessage CANCELLATION_REASON_REQUIRED = I18nMessage.of(Code.CANCELLATION_REASON_REQUIRED);

    /**
     * キャンセルの理由は 500 文字以内で入力してください。
     */
    public static final I18nMessage INVALID_CANCELLATION_REASON = I18nMessage.of(Code.INVALID_CANCELLATION_REASON);

    // ============================== コード定義 ==============================

    /**
     * メッセージコード
     */
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Code {

        /**
         * メールアドレスの形が正しくありません。
         */
        public static final String INVALID_EMAIL = "ecsite.order.invalid_email";

        /**
         * 氏名を 50 文字以内で入力してください。
         */
        public static final String INVALID_RECIPIENT_NAME = "ecsite.order.invalid_recipient_name";

        /**
         * 郵便番号は数字 7 桁で入力してください。
         */
        public static final String INVALID_POSTAL_CODE = "ecsite.order.invalid_postal_code";

        /**
         * 都道府県を選んでください。
         */
        public static final String INVALID_PREFECTURE = "ecsite.order.invalid_prefecture";

        /**
         * 住所を 200 文字以内で入力してください。
         */
        public static final String INVALID_ADDRESS = "ecsite.order.invalid_address";

        /**
         * 電話番号は数字 10〜11 桁で入力してください。
         */
        public static final String INVALID_PHONE_NUMBER = "ecsite.order.invalid_phone_number";

        /**
         * カートが空か、購入できない商品が含まれているため、注文できません。
         */
        public static final String CART_NOT_ORDERABLE = "ecsite.order.cart_not_orderable";

        /**
         * 在庫が足りない商品があります。カートを確かめてください。
         */
        public static final String OUT_OF_STOCK = "ecsite.order.out_of_stock";

        /**
         * 価格または税率が変わったため、総額が変わりました。内容を確かめて、もう一度注文してください。
         */
        public static final String TOTAL_AMOUNT_CHANGED = "ecsite.order.total_amount_changed";

        /**
         * 注文手続きを始めた後にカートが変わりました。もう一度注文手続きに進んでください。
         */
        public static final String CART_CHANGED = "ecsite.order.cart_changed";
        /**
         * この注文はキャンセルを申請できません。キャンセルをご希望の場合は、お問い合わせください。
         */
        public static final String CANCELLATION_NOT_REQUESTABLE = "ecsite.order.cancellation_not_requestable";

        /**
         * この注文には、承認・却下できるキャンセルの申請がありません。
         */
        public static final String CANCELLATION_NOT_PENDING = "ecsite.order.cancellation_not_pending";

        /**
         * キャンセルの理由を入力してください。
         */
        public static final String CANCELLATION_REASON_REQUIRED = "ecsite.order.cancellation_reason_required";

        /**
         * キャンセルの理由は 500 文字以内で入力してください。
         */
        public static final String INVALID_CANCELLATION_REASON = "ecsite.order.invalid_cancellation_reason";
    }
}

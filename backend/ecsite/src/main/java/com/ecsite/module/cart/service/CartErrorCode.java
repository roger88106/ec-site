package com.ecsite.module.cart.service;

import com.ecsite.common.error.ErrorCode;
import com.ecsite.common.error.ErrorCodeProvider;
import com.ecsite.common.error.ErrorType;
import com.ecsite.common.message.I18nMessage;
import lombok.Getter;

/**
 * カートモジュールのエラーコード
 */
public enum CartErrorCode implements ErrorCodeProvider {

    /**
     * 1 品目の数量が 1〜10 でない。合算した結果を含む（AC-75）
     */
    INVALID_CART_QUANTITY(ErrorType.VALIDATION_ERROR, CartMessage.INVALID_CART_QUANTITY),
    /**
     * 31 品目めを入れようとした（AC-75）
     */
    TOO_MANY_CART_ITEMS(ErrorType.BUSINESS_RULE_ERROR, CartMessage.TOO_MANY_CART_ITEMS),
    /**
     * 選べないバリアント（表示されない商品、販売停止、存在しない）を入れようとした（AC-72）
     */
    VARIANT_NOT_SELECTABLE(ErrorType.BUSINESS_RULE_ERROR, CartMessage.VARIANT_NOT_SELECTABLE),
    /**
     * 入れた後の数量が販売可能数を超える（AC-76）
     */
    QUANTITY_EXCEEDS_STOCK(ErrorType.BUSINESS_RULE_ERROR, CartMessage.QUANTITY_EXCEEDS_STOCK),
    ;

    /**
     * エラーコード
     */
    @Getter
    private final ErrorCode code;

    /**
     * コンストラクタ
     *
     * @param errorType エラータイプ
     * @param message   メッセージ
     */
    CartErrorCode(ErrorType errorType, I18nMessage message) {
        this.code = ErrorCode.of(this.name(), errorType, message);
    }
}

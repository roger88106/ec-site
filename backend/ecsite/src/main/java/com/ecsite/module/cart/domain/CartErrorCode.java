package com.ecsite.module.cart.domain;

import com.ecsite.common.error.ErrorCode;
import com.ecsite.common.error.ErrorCodeProvider;
import com.ecsite.common.error.ErrorType;
import lombok.Getter;

/**
 * カートモジュールのエラーコード
 */
public enum CartErrorCode implements ErrorCodeProvider {

    /**
     * 1 品目の数量が 1〜10 でない。合算した結果を含む
     */
    INVALID_CART_QUANTITY(ErrorType.VALIDATION_ERROR, "max"),
    /**
     * 31 品目めを入れようとした
     */
    TOO_MANY_CART_ITEMS(ErrorType.BUSINESS_RULE_ERROR, "max"),
    /**
     * 選べないバリアント（表示されない商品、販売停止、存在しない）を入れようとした
     */
    VARIANT_NOT_SELECTABLE(ErrorType.BUSINESS_RULE_ERROR),
    /**
     * 入れた後の数量が販売可能数を超える
     */
    QUANTITY_EXCEEDS_STOCK(ErrorType.BUSINESS_RULE_ERROR),
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
     * @param paramKeys 文に差し込む値の名前
     */
    CartErrorCode(ErrorType errorType, String... paramKeys) {
        this.code = ErrorCodeProvider.errorCode("cart", this.name(), errorType, paramKeys);
    }
}

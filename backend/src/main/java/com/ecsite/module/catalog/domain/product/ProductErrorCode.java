package com.ecsite.module.catalog.domain.product;

import com.ecsite.common.error.ErrorCode;
import com.ecsite.common.error.ErrorCodeProvider;
import com.ecsite.common.error.ErrorType;
import com.ecsite.common.message.I18nMessage;

/**
 * 商品のエラーコード
 */
public enum ProductErrorCode implements ErrorCodeProvider {

    /**
     * 商品品番の形式が正しくない（AC-15）
     */
    INVALID_PRODUCT_CODE(ErrorType.VALIDATION_ERROR, ProductMessage.INVALID_PRODUCT_CODE),
    /**
     * バリアント品番の形式が正しくない（AC-21）
     */
    INVALID_VARIANT_CODE(ErrorType.VALIDATION_ERROR, ProductMessage.INVALID_VARIANT_CODE),
    /**
     * 税抜価格が 1 円未満（AC-25）
     */
    INVALID_PRICE_EXCL_TAX(ErrorType.VALIDATION_ERROR, ProductMessage.INVALID_PRICE_EXCL_TAX),
    ;

    /**
     * エラーコード
     */
    private final ErrorCode errorCode;

    /**
     * コンストラクタ
     *
     * @param errorType エラータイプ
     * @param message   メッセージ
     */
    ProductErrorCode(ErrorType errorType, I18nMessage message) {
        this.errorCode = ErrorCode.of(this.name(), errorType, message);
    }

    /**
     * エラーコード取得
     *
     * @return {@link ErrorCode}
     */
    @Override
    public ErrorCode getCode() {
        return errorCode;
    }
}

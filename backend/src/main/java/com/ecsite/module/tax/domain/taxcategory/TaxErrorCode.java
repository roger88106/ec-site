package com.ecsite.module.tax.domain.taxcategory;

import com.ecsite.common.error.ErrorCode;
import com.ecsite.common.error.ErrorCodeProvider;
import com.ecsite.common.error.ErrorType;
import com.ecsite.common.message.I18nMessage;

/**
 * 税モジュールのエラーコード
 */
public enum TaxErrorCode implements ErrorCodeProvider {

    /**
     * 率が 0 以上 100 未満、小数第 2 位まででない（AC-7）
     */
    INVALID_RATE_PERCENT(ErrorType.VALIDATION_ERROR, TaxMessage.INVALID_RATE_PERCENT),
    /**
     * 適用期間の開始が終了より前でない（AC-2）
     */
    INVALID_EFFECTIVE_PERIOD(ErrorType.VALIDATION_ERROR, TaxMessage.INVALID_EFFECTIVE_PERIOD),
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
    TaxErrorCode(ErrorType errorType, I18nMessage message) {
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

package com.ecsite.module.shipping.domain;

import com.ecsite.common.error.ErrorCode;
import com.ecsite.common.error.ErrorCodeProvider;
import com.ecsite.common.error.ErrorType;
import lombok.Getter;

/**
 * 出荷のエラーコード
 */
public enum ShippingErrorCode implements ErrorCodeProvider {

    /**
     * 送り状番号が形に合わない（半角の数字とハイフンだけ、30 文字まで、数字が 1 つ以上）
     */
    TRACKING_NUMBER_INVALID(ErrorType.VALIDATION_ERROR, "maxLength"),

    /**
     * 同じ注文にすでに出荷がある
     */
    SHIPMENT_ALREADY_EXISTS(ErrorType.CONFLICT_ERROR),
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
    ShippingErrorCode(ErrorType errorType, String... paramKeys) {
        this.code = ErrorCodeProvider.errorCode("shipping", this.name(), errorType, paramKeys);
    }
}

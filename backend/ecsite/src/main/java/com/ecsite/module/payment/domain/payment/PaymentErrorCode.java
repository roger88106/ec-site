package com.ecsite.module.payment.domain.payment;

import com.ecsite.common.error.ErrorCode;
import com.ecsite.common.error.ErrorCodeProvider;
import com.ecsite.common.error.ErrorType;
import lombok.Getter;

/**
 * 決済モジュールのエラーコード
 */
public enum PaymentErrorCode implements ErrorCodeProvider {

    /**
     * 与信の期限が切れていて、売上を確定できない
     */
    AUTHORIZATION_EXPIRED(ErrorType.CONFLICT_ERROR),
    /**
     * 売上を確定できる状態でない（与信済みでも売上確定済みでもない）
     */
    PAYMENT_NOT_CAPTURABLE(ErrorType.CONFLICT_ERROR),
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
    PaymentErrorCode(ErrorType errorType, String... paramKeys) {
        this.code = ErrorCodeProvider.errorCode("payment", this.name(), errorType, paramKeys);
    }
}

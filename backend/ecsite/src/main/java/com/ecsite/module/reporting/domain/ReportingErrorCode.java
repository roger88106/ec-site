package com.ecsite.module.reporting.domain;

import com.ecsite.common.error.ErrorCode;
import com.ecsite.common.error.ErrorCodeProvider;
import com.ecsite.common.error.ErrorType;
import lombok.Getter;

/**
 * レポートのエラーコード
 */
public enum ReportingErrorCode implements ErrorCodeProvider {

    /**
     * 期間が正しくない（始まりが終わりより後、または長すぎる）
     */
    INVALID_PERIOD(ErrorType.VALIDATION_ERROR, "maxYears"),
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
    ReportingErrorCode(ErrorType errorType, String... paramKeys) {
        this.code = ErrorCodeProvider.errorCode("reporting", this.name(), errorType, paramKeys);
    }
}

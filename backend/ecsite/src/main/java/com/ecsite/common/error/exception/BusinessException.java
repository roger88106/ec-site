package com.ecsite.common.error.exception;

import com.ecsite.common.error.ErrorCodeProvider;
import com.ecsite.common.error.ErrorDetail;
import lombok.NonNull;

import java.util.List;

/**
 * ビジネス例外
 *
 * <p>HTTP の 4xx で返す。
 */
public class BusinessException extends BaseException {

    /**
     * ビジネス例外を作る。
     *
     * @param errorCodeProvider エラーコードプロバイダー
     * @return ビジネス例外
     */
    public static BusinessException of(@NonNull ErrorCodeProvider errorCodeProvider) {
        return new BusinessException(ErrorDetail.of(errorCodeProvider.getCode()));
    }

    /**
     * メッセージに差し込む値を持つビジネス例外を作る。
     *
     * @param errorCodeProvider エラーコードプロバイダー
     * @param messageParams     メッセージパラメータ
     * @return ビジネス例外
     */
    public static BusinessException of(@NonNull ErrorCodeProvider errorCodeProvider, @NonNull List<Object> messageParams) {
        return new BusinessException(ErrorDetail.of(errorCodeProvider.getCode(), messageParams));
    }

    /**
     * コンストラクタ。
     *
     * @param errorDetail エラー詳細情報
     */
    private BusinessException(ErrorDetail errorDetail) {
        super(errorDetail.errorCode().code(), null, errorDetail);
    }
}

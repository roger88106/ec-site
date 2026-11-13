package com.ecsite.common.error.exception;

import com.ecsite.common.error.ErrorCode;
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
     * @throws IllegalArgumentException システムの区分のエラーコード（プログラムの誤り）
     */
    public static BusinessException of(@NonNull ErrorCodeProvider errorCodeProvider) {
        return new BusinessException(ErrorDetail.of(businessCode(errorCodeProvider)));
    }

    /**
     * メッセージに差し込む値を持つビジネス例外を作る。
     *
     * @param errorCodeProvider エラーコードプロバイダー
     * @param messageParams     メッセージパラメータ
     * @return ビジネス例外
     * @throws IllegalArgumentException システムの区分のエラーコード（プログラムの誤り）
     */
    public static BusinessException of(@NonNull ErrorCodeProvider errorCodeProvider, @NonNull List<Object> messageParams) {
        return new BusinessException(ErrorDetail.of(businessCode(errorCodeProvider), messageParams));
    }

    /**
     * コンストラクタ。
     *
     * @param errorDetail エラー詳細情報
     */
    private BusinessException(ErrorDetail errorDetail) {
        super(errorDetail.errorCode().code(), null, errorDetail);
    }

// ============================== プライベートメソッド ==============================

    /**
     * 業務の区分のエラーコードを返す。
     *
     * @param errorCodeProvider エラーコードプロバイダー
     * @return エラーコード
     * @throws IllegalArgumentException システムの区分のエラーコード（プログラムの誤り）
     */
    private static ErrorCode businessCode(ErrorCodeProvider errorCodeProvider) {
        var code = errorCodeProvider.getCode();
        if (code.type().system()) {
            throw new IllegalArgumentException("システムの区分のエラーコードは、SystemException で投げる：" + code.code());
        }
        return code;
    }
}

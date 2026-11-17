package com.ecsite.common.error.exception;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.ErrorCodeProvider;
import com.ecsite.common.error.ErrorDetail;
import lombok.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * システム例外
 *
 * <p>HTTP の 5xx で返す。
 */
public class SystemException extends BaseException {

    /**
     * システムエラー
     */
    private static final ErrorDetail SYSTEM_ERROR_DETAIL = ErrorDetail.of(CommonErrorCode.SYSTEM_ERROR.getCode());

    /**
     * エラーコードを指定して、システム例外を作る。
     *
     * @param debugMessage      デバッグ用メッセージ
     * @param cause             原因となった例外
     * @param errorCodeProvider エラーコードプロバイダー
     * @return システム例外
     * @throws IllegalArgumentException 業務の区分のエラーコード（プログラムの誤り）
     */
    public static SystemException of(@NonNull String debugMessage, @Nullable Throwable cause, @NonNull ErrorCodeProvider errorCodeProvider) {
        var code = errorCodeProvider.getCode();
        if (!code.type().system()) {
            throw new IllegalArgumentException("業務の区分のエラーコードは、BusinessException で投げる：" + code.code());
        }
        return new SystemException(debugMessage, cause, ErrorDetail.of(code));
    }

    /**
     * エラーコードを共通のシステムエラー（{@code SYSTEM_ERROR}）にして、システム例外を作る。
     *
     * @param debugMessage デバッグ用メッセージ
     * @param cause        原因となった例外
     * @return システム例外
     */
    public static SystemException of(@NonNull String debugMessage, @Nullable Throwable cause) {
        return new SystemException(debugMessage, cause, SYSTEM_ERROR_DETAIL);
    }

    /**
     * コンストラクタ。
     *
     * @param debugMessage デバッグ用メッセージ
     * @param cause        原因となった例外
     * @param detail       エラー詳細情報
     */
    private SystemException(String debugMessage, @Nullable Throwable cause, ErrorDetail detail) {
        super(debugMessage, cause, detail);
    }
}

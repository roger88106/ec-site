package com.ecsite.common.error.exception;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.ErrorCodeProvider;
import com.ecsite.common.error.ErrorDetail;
import lombok.NonNull;

/**
 * システム上予期せぬエラー。 ※HTTP 500系
 */
public class SystemException extends BaseException {

    /**
     * システムエラー
     */
    private static final ErrorDetail SYSTEM_ERROR_DETAIL = ErrorDetail.of(CommonErrorCode.SYSTEM_ERROR.getCode());

    /**
     * {@code SystemException}の新しいインスタンスを作成して返します。
     *
     * @param debugMessage      デバッグ用メッセージ
     * @param cause             原因となった例外
     * @param errorCodeProvider エラーコードプロバイダー
     * @return 指定されたパラメータで初期化された新しい{@code SystemException}インスタンス
     */
    public static SystemException of(String debugMessage, Throwable cause, @NonNull ErrorCodeProvider errorCodeProvider) {
        return new SystemException(debugMessage, cause, ErrorDetail.of(errorCodeProvider.getCode()));
    }

    /**
     * {@code SystemException}の新しいインスタンスを作成して返します。
     *
     * @param debugMessage デバッグ用メッセージ
     * @param cause        原因となった例外
     * @return 指定されたパラメータで初期化された新しい{@code SystemException}インスタンス
     */
    public static SystemException of(String debugMessage, Throwable cause) {
        return new SystemException(debugMessage, cause, SYSTEM_ERROR_DETAIL);
    }

    /**
     * コンストラクタ。
     *
     * @param debugMessage デバッグ用メッセージ
     * @param cause        原因となった例外
     * @param detail       エラー詳細
     */
    private SystemException(String debugMessage, Throwable cause, ErrorDetail detail) {
        super(debugMessage, cause, detail);
    }
}

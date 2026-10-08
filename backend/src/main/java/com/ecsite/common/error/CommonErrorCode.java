package com.ecsite.common.error;

import com.ecsite.common.message.CommonMessage;
import com.ecsite.common.message.I18nMessage;

/**
 * 共通エラーコード
 */
public enum CommonErrorCode implements ErrorCodeProvider {

    /**
     * 見つかりませんでした。
     */
    NOT_FOUND(ErrorType.NOT_FOUND_ERROR, CommonMessage.NOT_FOUND),
    /**
     * 更新処理排他エラー
     */
    CONCURRENT_MODIFICATION(ErrorType.CONFLICT_ERROR, CommonMessage.CONCURRENT_MODIFICATION),
    /**
     * リクエスト内容の検証エラー
     */
    INVALID_REQUEST(ErrorType.VALIDATION_ERROR, CommonMessage.INVALID_REQUEST),
    /**
     * 許可されていない HTTP メソッド。HTTP ステータス（405）は Spring MVC の例外が決める
     */
    METHOD_NOT_ALLOWED(ErrorType.VALIDATION_ERROR, CommonMessage.METHOD_NOT_ALLOWED),
    /**
     * リポジトリ復元エラー
     */
    RESTORE_ERROR(ErrorType.RESTORE_ERROR, CommonMessage.SYSTEM_ERROR),
    /**
     * システムエラー
     */
    SYSTEM_ERROR(ErrorType.SYSTEM_ERROR, CommonMessage.SYSTEM_ERROR);

    /**
     * エラーコード
     */
    private final ErrorCode errorCode;

    /**
     * エラーコード取得
     *
     * @return {@link ErrorCode}
     */
    @Override
    public ErrorCode getCode() {
        return errorCode;
    }

    /**
     * コンストラクタ
     *
     * @param errorType エラータイプ
     * @param message   エラーメッセージ
     */
    CommonErrorCode(ErrorType errorType, I18nMessage message) {
        errorCode = ErrorCode.of(this.name(), errorType, message);
    }
}

package com.ecsite.common.error;

import com.ecsite.common.message.CommonMessage;
import com.ecsite.common.message.I18nMessage;
import lombok.Getter;

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
     * 認証がない、または token が無効
     */
    UNAUTHENTICATED(ErrorType.UNAUTHENTICATED_ERROR, CommonMessage.UNAUTHENTICATED),
    /**
     * 操作に必要な権限がない
     */
    FORBIDDEN(ErrorType.FORBIDDEN_ERROR, CommonMessage.FORBIDDEN),
    /**
     * 金額が負
     */
    INVALID_MONEY(ErrorType.VALIDATION_ERROR, CommonMessage.INVALID_MONEY),
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
    @Getter
    private final ErrorCode code;

    /**
     * コンストラクタ
     *
     * @param errorType エラータイプ
     * @param message   エラーメッセージ
     */
    CommonErrorCode(ErrorType errorType, I18nMessage message) {
        this.code = ErrorCode.of(this.name(), errorType, message);
    }
}

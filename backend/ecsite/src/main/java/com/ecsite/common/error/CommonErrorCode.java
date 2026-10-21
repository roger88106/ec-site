package com.ecsite.common.error;

import lombok.Getter;

/**
 * 共通エラーコード
 */
public enum CommonErrorCode implements ErrorCodeProvider {

    /**
     * 見つかりませんでした。
     */
    NOT_FOUND(ErrorType.NOT_FOUND_ERROR),
    /**
     * 更新処理排他エラー
     */
    CONCURRENT_MODIFICATION(ErrorType.CONFLICT_ERROR),
    /**
     * リクエスト内容の検証エラー
     */
    INVALID_REQUEST(ErrorType.VALIDATION_ERROR),
    /**
     * 許可されていない HTTP メソッド。HTTP ステータス（405）は Spring MVC の例外が決める
     */
    METHOD_NOT_ALLOWED(ErrorType.VALIDATION_ERROR),
    /**
     * 認証がない、または token が無効
     */
    UNAUTHENTICATED(ErrorType.UNAUTHENTICATED_ERROR),
    /**
     * 操作に必要な権限がない
     */
    FORBIDDEN(ErrorType.FORBIDDEN_ERROR),
    /**
     * 金額が負
     */
    INVALID_MONEY(ErrorType.VALIDATION_ERROR, "min"),
    /**
     * リポジトリ復元エラー
     */
    RESTORE_ERROR(ErrorType.RESTORE_ERROR),
    /**
     * システムエラー
     */
    SYSTEM_ERROR(ErrorType.SYSTEM_ERROR);

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
    CommonErrorCode(ErrorType errorType, String... paramKeys) {
        this.code = ErrorCodeProvider.errorCode("common", this.name(), errorType, paramKeys);
    }
}

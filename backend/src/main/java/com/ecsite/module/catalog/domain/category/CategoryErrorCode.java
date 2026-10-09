package com.ecsite.module.catalog.domain.category;

import com.ecsite.common.error.ErrorCode;
import com.ecsite.common.error.ErrorCodeProvider;
import com.ecsite.common.error.ErrorType;
import com.ecsite.common.message.I18nMessage;

/**
 * カテゴリのエラーコード
 */
public enum CategoryErrorCode implements ErrorCodeProvider {

    /**
     * カテゴリの名称が 1 文字以上 50 文字以内でない
     */
    INVALID_CATEGORY_NAME(ErrorType.VALIDATION_ERROR, CategoryMessage.INVALID_CATEGORY_NAME),
    /**
     * 4 階層目のカテゴリを登録しようとした（AC-9）
     */
    CATEGORY_DEPTH_EXCEEDED(ErrorType.BUSINESS_RULE_ERROR, CategoryMessage.CATEGORY_DEPTH_EXCEEDED),
    /**
     * 同じ親の下で名称が重複している（AC-10）
     */
    CATEGORY_NAME_ALREADY_EXISTS(ErrorType.CONFLICT_ERROR, CategoryMessage.CATEGORY_NAME_ALREADY_EXISTS),
    /**
     * 並び順の変更で送られたカテゴリが、兄弟と過不足なく一致しない（AC-11）
     */
    INVALID_CATEGORY_ORDER(ErrorType.VALIDATION_ERROR, CategoryMessage.INVALID_CATEGORY_ORDER),
    /**
     * 子カテゴリを持つカテゴリを削除しようとした（AC-12）
     */
    CATEGORY_HAS_CHILDREN(ErrorType.BUSINESS_RULE_ERROR, CategoryMessage.CATEGORY_HAS_CHILDREN),
    ;

    /**
     * エラーコード
     */
    private final ErrorCode errorCode;

    /**
     * コンストラクタ
     *
     * @param errorType エラータイプ
     * @param message   メッセージ
     */
    CategoryErrorCode(ErrorType errorType, I18nMessage message) {
        this.errorCode = ErrorCode.of(this.name(), errorType, message);
    }

    /**
     * エラーコード取得
     *
     * @return {@link ErrorCode}
     */
    @Override
    public ErrorCode getCode() {
        return errorCode;
    }
}

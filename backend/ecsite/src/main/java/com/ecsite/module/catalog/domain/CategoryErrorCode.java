package com.ecsite.module.catalog.domain;

import com.ecsite.common.error.ErrorCode;
import com.ecsite.common.error.ErrorCodeProvider;
import com.ecsite.common.error.ErrorType;
import lombok.Getter;

/**
 * カテゴリのエラーコード
 */
public enum CategoryErrorCode implements ErrorCodeProvider {

    /**
     * カテゴリの名称が 1 文字以上 50 文字以内でない
     */
    INVALID_CATEGORY_NAME(ErrorType.VALIDATION_ERROR, "max"),
    /**
     * 4 階層目のカテゴリを登録しようとした
     */
    CATEGORY_DEPTH_EXCEEDED(ErrorType.BUSINESS_RULE_ERROR, "max"),
    /**
     * 同じ親の下で名称が重複している
     */
    CATEGORY_NAME_ALREADY_EXISTS(ErrorType.CONFLICT_ERROR),
    /**
     * 並び順の変更で送られたカテゴリが、兄弟と過不足なく一致しない
     */
    INVALID_CATEGORY_ORDER(ErrorType.VALIDATION_ERROR),
    /**
     * 子カテゴリを持つカテゴリを削除しようとした
     */
    CATEGORY_HAS_CHILDREN(ErrorType.BUSINESS_RULE_ERROR),
    /**
     * 商品が所属しているカテゴリを削除しようとした
     */
    CATEGORY_HAS_PRODUCTS(ErrorType.BUSINESS_RULE_ERROR),
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
    CategoryErrorCode(ErrorType errorType, String... paramKeys) {
        this.code = ErrorCodeProvider.errorCode("catalog", this.name(), errorType, paramKeys);
    }

}

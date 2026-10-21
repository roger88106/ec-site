package com.ecsite.module.inventory.domain.stock;

import com.ecsite.common.error.ErrorCode;
import com.ecsite.common.error.ErrorCodeProvider;
import com.ecsite.common.error.ErrorType;
import lombok.Getter;

/**
 * 在庫モジュールのエラーコード
 */
public enum InventoryErrorCode implements ErrorCodeProvider {

    /**
     * 増減量が 0、または ±1,000,000 を超える
     */
    INVALID_STOCK_DELTA(ErrorType.VALIDATION_ERROR, "max"),
    /**
     * 理由が「その他」なのにメモがない
     */
    STOCK_MEMO_REQUIRED(ErrorType.VALIDATION_ERROR),
    /**
     * メモが 500 文字を超えている
     */
    INVALID_STOCK_MEMO(ErrorType.VALIDATION_ERROR, "max"),
    /**
     * 調整後の在庫数が負になる
     */
    INSUFFICIENT_STOCK(ErrorType.BUSINESS_RULE_ERROR),
    /**
     * 運営者の調整で、注文に由来する理由を選んだ
     */
    ORDER_REASON_NOT_SELECTABLE(ErrorType.BUSINESS_RULE_ERROR),
    /**
     * 運営者の調整の後の在庫数が 1,000,000 を超える
     */
    STOCK_QUANTITY_EXCEEDED(ErrorType.BUSINESS_RULE_ERROR, "max"),
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
    InventoryErrorCode(ErrorType errorType, String... paramKeys) {
        this.code = ErrorCodeProvider.errorCode("inventory", this.name(), errorType, paramKeys);
    }

}

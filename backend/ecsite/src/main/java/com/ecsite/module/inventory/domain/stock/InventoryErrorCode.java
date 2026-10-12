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
     * 増減量が 0
     */
    INVALID_STOCK_DELTA(ErrorType.VALIDATION_ERROR),
    /**
     * 理由が「その他」なのにメモがない（AC-46）
     */
    STOCK_MEMO_REQUIRED(ErrorType.VALIDATION_ERROR),
    /**
     * メモが 500 文字を超えている
     */
    INVALID_STOCK_MEMO(ErrorType.VALIDATION_ERROR),
    /**
     * 調整後の在庫数が負になる（AC-43）
     */
    INSUFFICIENT_STOCK(ErrorType.BUSINESS_RULE_ERROR),
    /**
     * 運営者の調整で、注文に由来する理由を選んだ（AC-107）
     */
    ORDER_REASON_NOT_SELECTABLE(ErrorType.BUSINESS_RULE_ERROR),
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

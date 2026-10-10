package com.ecsite.module.inventory.domain.stock;

import com.ecsite.common.error.ErrorCode;
import com.ecsite.common.error.ErrorCodeProvider;
import com.ecsite.common.error.ErrorType;
import com.ecsite.common.message.I18nMessage;
import lombok.Getter;

/**
 * 在庫モジュールのエラーコード
 */
public enum InventoryErrorCode implements ErrorCodeProvider {

    /**
     * 増減量が 0
     */
    INVALID_STOCK_DELTA(ErrorType.VALIDATION_ERROR, InventoryMessage.INVALID_STOCK_DELTA),
    /**
     * 理由が「その他」なのにメモがない（AC-46）
     */
    STOCK_MEMO_REQUIRED(ErrorType.VALIDATION_ERROR, InventoryMessage.STOCK_MEMO_REQUIRED),
    /**
     * メモが 500 文字を超えている
     */
    INVALID_STOCK_MEMO(ErrorType.VALIDATION_ERROR, InventoryMessage.INVALID_STOCK_MEMO),
    /**
     * 調整後の在庫数が負になる（AC-43）
     */
    INSUFFICIENT_STOCK(ErrorType.BUSINESS_RULE_ERROR, InventoryMessage.INSUFFICIENT_STOCK),
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
     * @param message   メッセージ
     */
    InventoryErrorCode(ErrorType errorType, I18nMessage message) {
        this.code = ErrorCode.of(this.name(), errorType, message);
    }

}

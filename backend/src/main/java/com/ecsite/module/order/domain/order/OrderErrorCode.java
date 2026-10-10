package com.ecsite.module.order.domain.order;

import com.ecsite.common.error.ErrorCode;
import com.ecsite.common.error.ErrorCodeProvider;
import com.ecsite.common.error.ErrorType;
import com.ecsite.common.message.I18nMessage;
import lombok.Getter;

/**
 * 注文モジュールのエラーコード
 */
public enum OrderErrorCode implements ErrorCodeProvider {

    /**
     * メールアドレスの形が違う（AC-86）
     */
    INVALID_EMAIL(ErrorType.VALIDATION_ERROR, OrderMessage.INVALID_EMAIL),
    /**
     * 氏名が空白、または 50 文字を超える（AC-86）
     */
    INVALID_RECIPIENT_NAME(ErrorType.VALIDATION_ERROR, OrderMessage.INVALID_RECIPIENT_NAME),
    /**
     * 郵便番号が数字 7 桁でない（AC-86）
     */
    INVALID_POSTAL_CODE(ErrorType.VALIDATION_ERROR, OrderMessage.INVALID_POSTAL_CODE),
    /**
     * 47 都道府県のいずれでもない（AC-86）
     */
    INVALID_PREFECTURE(ErrorType.VALIDATION_ERROR, OrderMessage.INVALID_PREFECTURE),
    /**
     * 住所が空白、または 200 文字を超える（AC-86）
     */
    INVALID_ADDRESS(ErrorType.VALIDATION_ERROR, OrderMessage.INVALID_ADDRESS),
    /**
     * 電話番号が数字 10〜11 桁でない（AC-86）
     */
    INVALID_PHONE_NUMBER(ErrorType.VALIDATION_ERROR, OrderMessage.INVALID_PHONE_NUMBER),
    /**
     * 空のカート、または購入できない品目を含むカート（AC-88）
     */
    CART_NOT_ORDERABLE(ErrorType.BUSINESS_RULE_ERROR, OrderMessage.CART_NOT_ORDERABLE),
    /**
     * 在庫が足りない（AC-82、83、87）
     */
    OUT_OF_STOCK(ErrorType.CONFLICT_ERROR, OrderMessage.OUT_OF_STOCK),
    /**
     * 確認した総額と、注文時点の総額が違う（AC-89）
     */
    TOTAL_AMOUNT_CHANGED(ErrorType.CONFLICT_ERROR, OrderMessage.TOTAL_AMOUNT_CHANGED),
    /**
     * 注文手続きを始めた後にカートが変わった
     */
    CART_CHANGED(ErrorType.CONFLICT_ERROR, OrderMessage.CART_CHANGED),
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
    OrderErrorCode(ErrorType errorType, I18nMessage message) {
        this.code = ErrorCode.of(this.name(), errorType, message);
    }
}

package com.ecsite.module.order.domain.order;

import com.ecsite.common.error.ErrorCode;
import com.ecsite.common.error.ErrorCodeProvider;
import com.ecsite.common.error.ErrorType;
import lombok.Getter;

/**
 * 注文モジュールのエラーコード
 */
public enum OrderErrorCode implements ErrorCodeProvider {

    /**
     * メールアドレスの形が違う
     */
    INVALID_EMAIL(ErrorType.VALIDATION_ERROR),
    /**
     * 会員のメールアドレスが確認済みでない
     */
    EMAIL_NOT_VERIFIED(ErrorType.BUSINESS_RULE_ERROR),
    /**
     * 氏名が空白、または 50 文字を超える
     */
    INVALID_RECIPIENT_NAME(ErrorType.VALIDATION_ERROR, "max"),
    /**
     * 郵便番号が数字 7 桁でない
     */
    INVALID_POSTAL_CODE(ErrorType.VALIDATION_ERROR),
    /**
     * 47 都道府県のいずれでもない
     */
    INVALID_PREFECTURE(ErrorType.VALIDATION_ERROR),
    /**
     * 住所が空白、または 200 文字を超える
     */
    INVALID_ADDRESS(ErrorType.VALIDATION_ERROR, "max"),
    /**
     * 電話番号が数字 10〜11 桁でない
     */
    INVALID_PHONE_NUMBER(ErrorType.VALIDATION_ERROR),
    /**
     * 空のカート、または購入できない品目を含むカート
     */
    CART_NOT_ORDERABLE(ErrorType.BUSINESS_RULE_ERROR),
    /**
     * 在庫が足りない
     */
    OUT_OF_STOCK(ErrorType.CONFLICT_ERROR),
    /**
     * 確認した総額と、注文時点の総額が違う
     */
    TOTAL_AMOUNT_CHANGED(ErrorType.CONFLICT_ERROR),
    /**
     * 注文手続きを始めた後にカートが変わった
     */
    CART_CHANGED(ErrorType.CONFLICT_ERROR),
    /**
     * 申請できない注文（却下済み、キャンセル済み。申請は 1 回だけ）
     */
    CANCELLATION_NOT_REQUESTABLE(ErrorType.BUSINESS_RULE_ERROR),
    /**
     * 申請中でない注文の承認・却下
     */
    CANCELLATION_NOT_PENDING(ErrorType.BUSINESS_RULE_ERROR),
    /**
     * 運営者のキャンセルの理由がない
     */
    CANCELLATION_REASON_REQUIRED(ErrorType.VALIDATION_ERROR),
    /**
     * キャンセルの理由が 500 文字を超える
     */
    INVALID_CANCELLATION_REASON(ErrorType.VALIDATION_ERROR, "max"),
    /**
     * 支払待ちの注文の運営者のキャンセル（支払の期限で自動でキャンセルになる）
     */
    AWAITING_PAYMENT(ErrorType.BUSINESS_RULE_ERROR),
    /**
     * 支払えない注文の支払の画面（支払待ちでない、支払の期限の後）
     */
    ORDER_NOT_PAYABLE(ErrorType.CONFLICT_ERROR),
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
    OrderErrorCode(ErrorType errorType, String... paramKeys) {
        this.code = ErrorCodeProvider.errorCode("order", this.name(), errorType, paramKeys);
    }
}

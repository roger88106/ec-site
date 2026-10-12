package com.ecsite.module.tax.domain.taxcategory;

import com.ecsite.common.error.ErrorCode;
import com.ecsite.common.error.ErrorCodeProvider;
import com.ecsite.common.error.ErrorType;
import com.ecsite.common.message.I18nMessage;
import lombok.Getter;

/**
 * 税モジュールのエラーコード
 */
public enum TaxErrorCode implements ErrorCodeProvider {

    /**
     * 率が 0 以上 100 未満、小数第 2 位まででない（AC-7）
     */
    INVALID_RATE_PERCENT(ErrorType.VALIDATION_ERROR, TaxMessage.INVALID_RATE_PERCENT),
    /**
     * 適用期間の開始が終了より前でない（AC-2）
     */
    INVALID_EFFECTIVE_PERIOD(ErrorType.VALIDATION_ERROR, TaxMessage.INVALID_EFFECTIVE_PERIOD),
    /**
     * 税区分の名称が 1 文字以上 50 文字以内でない
     */
    INVALID_TAX_CATEGORY_NAME(ErrorType.VALIDATION_ERROR, TaxMessage.INVALID_TAX_CATEGORY_NAME),
    /**
     * 税区分の名称が重複している（AC-1）
     */
    TAX_CATEGORY_NAME_ALREADY_EXISTS(ErrorType.CONFLICT_ERROR, TaxMessage.TAX_CATEGORY_NAME_ALREADY_EXISTS),
    /**
     * 置き換え後の税率の一覧が空
     */
    TAX_RATES_REQUIRED(ErrorType.BUSINESS_RULE_ERROR, TaxMessage.TAX_RATES_REQUIRED),
    /**
     * 税区分にない税率、または重複した税率が指定された
     */
    INVALID_TAX_RATE_ID(ErrorType.VALIDATION_ERROR, TaxMessage.INVALID_TAX_RATE_ID),
    /**
     * 税率の適用期間が重複している（AC-3）
     */
    TAX_RATE_PERIODS_OVERLAP(ErrorType.BUSINESS_RULE_ERROR, TaxMessage.TAX_RATE_PERIODS_OVERLAP),
    /**
     * 税率の適用期間の間に途切れがある（AC-3）
     */
    TAX_RATE_PERIODS_GAP(ErrorType.BUSINESS_RULE_ERROR, TaxMessage.TAX_RATE_PERIODS_GAP),
    /**
     * 最後の税率の適用終了日時が空白でない（AC-3）
     */
    TAX_RATE_LAST_PERIOD_NOT_OPEN_ENDED(ErrorType.BUSINESS_RULE_ERROR, TaxMessage.TAX_RATE_LAST_PERIOD_NOT_OPEN_ENDED),
    /**
     * 現在より前の日時を、適用開始日時・適用終了日時として新たに設定しようとした（AC-5）
     */
    TAX_RATE_PAST_DATETIME(ErrorType.BUSINESS_RULE_ERROR, TaxMessage.TAX_RATE_PAST_DATETIME),
    /**
     * 開始済みの税率の率・適用開始日時を変更、または削除しようとした（AC-5）
     */
    TAX_RATE_STARTED_UNCHANGEABLE(ErrorType.BUSINESS_RULE_ERROR, TaxMessage.TAX_RATE_STARTED_UNCHANGEABLE),
    /**
     * 終了済みの税率を変更、または削除しようとした（AC-5）
     */
    TAX_RATE_ENDED_UNCHANGEABLE(ErrorType.BUSINESS_RULE_ERROR, TaxMessage.TAX_RATE_ENDED_UNCHANGEABLE),
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
    TaxErrorCode(ErrorType errorType, I18nMessage message) {
        this.code = ErrorCode.of(this.name(), errorType, message);
    }

}

package com.ecsite.module.tax.domain.taxcategory;

import com.ecsite.common.message.I18nMessage;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 税モジュールのメッセージ
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TaxMessage {

    /**
     * 税率は 0% 以上 100% 未満、小数第 2 位までにしてください。
     */
    public static final I18nMessage INVALID_RATE_PERCENT = I18nMessage.of(Code.INVALID_RATE_PERCENT);

    /**
     * 適用開始日時は、適用終了日時より前にしてください。
     */
    public static final I18nMessage INVALID_EFFECTIVE_PERIOD = I18nMessage.of(Code.INVALID_EFFECTIVE_PERIOD);

    /**
     * 税区分の名称は 1 文字以上 50 文字以内にしてください。
     */
    public static final I18nMessage INVALID_TAX_CATEGORY_NAME = I18nMessage.of(Code.INVALID_TAX_CATEGORY_NAME);

    /**
     * 同じ名称の税区分がすでにあります。
     */
    public static final I18nMessage TAX_CATEGORY_NAME_ALREADY_EXISTS = I18nMessage.of(Code.TAX_CATEGORY_NAME_ALREADY_EXISTS);

    /**
     * 税率を 1 件以上指定してください。
     */
    public static final I18nMessage TAX_RATES_REQUIRED = I18nMessage.of(Code.TAX_RATES_REQUIRED);

    /**
     * 税区分にない税率、または重複した税率が指定されています。
     */
    public static final I18nMessage INVALID_TAX_RATE_ID = I18nMessage.of(Code.INVALID_TAX_RATE_ID);

    /**
     * 税率の適用期間が重複しています。
     */
    public static final I18nMessage TAX_RATE_PERIODS_OVERLAP = I18nMessage.of(Code.TAX_RATE_PERIODS_OVERLAP);

    /**
     * 税率の適用期間の間に途切れがあります。
     */
    public static final I18nMessage TAX_RATE_PERIODS_GAP = I18nMessage.of(Code.TAX_RATE_PERIODS_GAP);

    /**
     * 最後の税率の適用終了日時は空白にしてください。
     */
    public static final I18nMessage TAX_RATE_LAST_PERIOD_NOT_OPEN_ENDED = I18nMessage.of(Code.TAX_RATE_LAST_PERIOD_NOT_OPEN_ENDED);

    /**
     * 現在より前の日時は、適用開始日時・適用終了日時に設定できません。
     */
    public static final I18nMessage TAX_RATE_PAST_DATETIME = I18nMessage.of(Code.TAX_RATE_PAST_DATETIME);

    /**
     * 適用が始まった税率は、率と適用開始日時を変更できず、削除もできません。
     */
    public static final I18nMessage TAX_RATE_STARTED_UNCHANGEABLE = I18nMessage.of(Code.TAX_RATE_STARTED_UNCHANGEABLE);

    /**
     * 適用が終わった税率は、変更も削除もできません。
     */
    public static final I18nMessage TAX_RATE_ENDED_UNCHANGEABLE = I18nMessage.of(Code.TAX_RATE_ENDED_UNCHANGEABLE);

    // ============================== コード定義 ==============================

    /**
     * メッセージコード
     */
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Code {
        /**
         * 税率は 0% 以上 100% 未満、小数第 2 位までにしてください。
         */
        public static final String INVALID_RATE_PERCENT = "ecsite.tax.invalid_rate_percent";

        /**
         * 適用開始日時は、適用終了日時より前にしてください。
         */
        public static final String INVALID_EFFECTIVE_PERIOD = "ecsite.tax.invalid_effective_period";

        /**
         * 税区分の名称は 1 文字以上 50 文字以内にしてください。
         */
        public static final String INVALID_TAX_CATEGORY_NAME = "ecsite.tax.invalid_tax_category_name";

        /**
         * 同じ名称の税区分がすでにあります。
         */
        public static final String TAX_CATEGORY_NAME_ALREADY_EXISTS = "ecsite.tax.tax_category_name_already_exists";

        /**
         * 税率を 1 件以上指定してください。
         */
        public static final String TAX_RATES_REQUIRED = "ecsite.tax.tax_rates_required";

        /**
         * 税区分にない税率、または重複した税率が指定されています。
         */
        public static final String INVALID_TAX_RATE_ID = "ecsite.tax.invalid_tax_rate_id";

        /**
         * 税率の適用期間が重複しています。
         */
        public static final String TAX_RATE_PERIODS_OVERLAP = "ecsite.tax.tax_rate_periods_overlap";

        /**
         * 税率の適用期間の間に途切れがあります。
         */
        public static final String TAX_RATE_PERIODS_GAP = "ecsite.tax.tax_rate_periods_gap";

        /**
         * 最後の税率の適用終了日時は空白にしてください。
         */
        public static final String TAX_RATE_LAST_PERIOD_NOT_OPEN_ENDED = "ecsite.tax.tax_rate_last_period_not_open_ended";

        /**
         * 現在より前の日時は、適用開始日時・適用終了日時に設定できません。
         */
        public static final String TAX_RATE_PAST_DATETIME = "ecsite.tax.tax_rate_past_datetime";

        /**
         * 適用が始まった税率は、率と適用開始日時を変更できず、削除もできません。
         */
        public static final String TAX_RATE_STARTED_UNCHANGEABLE = "ecsite.tax.tax_rate_started_unchangeable";

        /**
         * 適用が終わった税率は、変更も削除もできません。
         */
        public static final String TAX_RATE_ENDED_UNCHANGEABLE = "ecsite.tax.tax_rate_ended_unchangeable";
    }
}

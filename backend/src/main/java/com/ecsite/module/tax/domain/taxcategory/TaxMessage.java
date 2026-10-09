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
    }
}

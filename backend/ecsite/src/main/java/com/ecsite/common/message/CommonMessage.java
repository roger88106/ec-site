package com.ecsite.common.message;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 共通メッセージ
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CommonMessage {

    /**
     * 見つかりませんでした。
     */
    public static final I18nMessage NOT_FOUND = I18nMessage.of(Code.NOT_FOUND);

    /**
     * 他の人が先に更新しました。再読み込みしてください。
     */
    public static final I18nMessage CONCURRENT_MODIFICATION = I18nMessage.of(Code.CONCURRENT_MODIFICATION);

    /**
     * 入力内容に誤りがあります。
     */
    public static final I18nMessage INVALID_REQUEST = I18nMessage.of(Code.INVALID_REQUEST);

    /**
     * この操作は許可されていません。
     */
    public static final I18nMessage METHOD_NOT_ALLOWED = I18nMessage.of(Code.METHOD_NOT_ALLOWED);

    /**
     * ログインしてください。
     */
    public static final I18nMessage UNAUTHENTICATED = I18nMessage.of(Code.UNAUTHENTICATED);

    /**
     * この操作を行う権限がありません。
     */
    public static final I18nMessage FORBIDDEN = I18nMessage.of(Code.FORBIDDEN);

    /**
     * 金額は 0 円以上にしてください。
     */
    public static final I18nMessage INVALID_MONEY = I18nMessage.of(Code.INVALID_MONEY);

    /**
     * 予期しないエラーが発生しました。
     */
    public static final I18nMessage SYSTEM_ERROR = I18nMessage.of(Code.SYSTEM_ERROR);

    // ============================== コード定義 ==============================

    /**
     * メッセージコード
     */
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Code {
        /**
         * 見つかりませんでした。
         */
        public static final String NOT_FOUND = "ecsite.common.not_found";

        /**
         * 他の人が先に更新しました。再読み込みしてください。
         */
        public static final String CONCURRENT_MODIFICATION = "ecsite.common.concurrent_modification";

        /**
         * 入力内容に誤りがあります。
         */
        public static final String INVALID_REQUEST = "ecsite.common.invalid_request";

        /**
         * この操作は許可されていません。
         */
        public static final String METHOD_NOT_ALLOWED = "ecsite.common.method_not_allowed";

        /**
         * ログインしてください。
         */
        public static final String UNAUTHENTICATED = "ecsite.common.unauthenticated";

        /**
         * この操作を行う権限がありません。
         */
        public static final String FORBIDDEN = "ecsite.common.forbidden";

        /**
         * 金額は 0 円以上にしてください。
         */
        public static final String INVALID_MONEY = "ecsite.common.invalid_money";

        /**
         * 予期しないエラーが発生しました。
         */
        public static final String SYSTEM_ERROR = "ecsite.common.system_error";
    }
}

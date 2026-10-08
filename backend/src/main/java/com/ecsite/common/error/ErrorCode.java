package com.ecsite.common.error;

import com.ecsite.common.message.I18nMessage;
import lombok.NonNull;

/**
 * エラーコード
 *
 * @param code     エラーコード
 * @param type     エラータイプ
 * @param message  メッセージ
 * @param exposure エラー情報の公開レベル
 */
public record ErrorCode(@NonNull String code, @NonNull ErrorType type, @NonNull I18nMessage message,
                        @NonNull ErrorExposure exposure) {

    /**
     * エラー情報の公開レベル
     */
    public enum ErrorExposure {
        /**
         * クライアントにそのまま公開可能。
         */
        EXPOSABLE,

        /**
         * 汎用エラーへ格下げする必要がある。
         */
        DOWNGRADE_REQUIRED,
    }

    // ============================== ファクトリー ==============================

    /**
     * エラーコードを作成するファクトリー
     *
     * @param code    エラーコード
     * @param type    エラータイプ
     * @param message メッセージ
     * @return {@link ErrorCode}
     */
    public static ErrorCode of(String code, ErrorType type, I18nMessage message) {
        return new ErrorCode(code, type, message, ErrorExposure.EXPOSABLE);
    }

    /**
     * エラーコードを作成するファクトリー
     *
     * @param code     エラーコード
     * @param type     エラータイプ
     * @param message  メッセージ
     * @param exposure エラー情報の公開レベル
     * @return {@link ErrorCode}
     */
    public static ErrorCode of(String code, ErrorType type, I18nMessage message, ErrorExposure exposure) {
        return new ErrorCode(code, type, message, exposure);
    }
}

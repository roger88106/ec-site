package com.ecsite.common.error;

import com.ecsite.common.message.I18nMessage;
import lombok.NonNull;

/**
 * エラーコード
 *
 * @param code    エラーコード
 * @param type    エラータイプ
 * @param message メッセージ
 */
public record ErrorCode(@NonNull String code, @NonNull ErrorType type, @NonNull I18nMessage message) {

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
        return new ErrorCode(code, type, message);
    }
}

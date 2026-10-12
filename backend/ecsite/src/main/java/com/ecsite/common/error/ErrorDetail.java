package com.ecsite.common.error;

import com.ecsite.common.message.ErrorMessage;
import lombok.NonNull;

import java.util.List;


/**
 * エラー詳細情報。
 *
 * @param errorCode エラーコード
 * @param message   メッセージ
 */
public record ErrorDetail(@NonNull ErrorCode errorCode, @NonNull ErrorMessage message) {

    /**
     * エラー詳細情報を生成する。
     *
     * @param errorCode     エラーコード
     * @param messageParams メッセージパラメータ
     * @return {@link ErrorDetail}
     */
    public static ErrorDetail of(ErrorCode errorCode, List<Object> messageParams) {
        return new ErrorDetail(errorCode, ErrorMessage.of(errorCode, messageParams));
    }

    /**
     * エラー詳細情報を生成する。
     *
     * @param errorCode エラーコード
     * @return {@link ErrorDetail}
     */
    public static ErrorDetail of(ErrorCode errorCode) {
        return new ErrorDetail(errorCode, ErrorMessage.of(errorCode, List.of()));
    }
}

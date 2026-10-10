package com.ecsite.module.media.service;

import com.ecsite.common.error.ErrorCode;
import com.ecsite.common.error.ErrorCodeProvider;
import com.ecsite.common.error.ErrorType;
import com.ecsite.common.message.I18nMessage;

/**
 * メディアのエラーコード。<br />
 * コードの文字列は、商品画像 API の契約（admin.yaml）のとおり。商品カタログから移したときに変えていない。
 */
public enum MediaErrorCode implements ErrorCodeProvider {

    /**
     * 画像の形式が対象外（AC-39）
     */
    UNSUPPORTED_IMAGE_FORMAT(ErrorType.VALIDATION_ERROR, MediaMessage.UNSUPPORTED_IMAGE_FORMAT),
    /**
     * 画像の大きさが範囲の外（0 バイト、または 10MB を超える。AC-39）
     */
    IMAGE_TOO_LARGE(ErrorType.VALIDATION_ERROR, MediaMessage.IMAGE_TOO_LARGE),
    /**
     * 受付がない（期限切れで消えたもの、別の範囲のものを含む）
     */
    IMAGE_UPLOAD_NOT_FOUND(ErrorType.VALIDATION_ERROR, MediaMessage.IMAGE_UPLOAD_NOT_FOUND),
    /**
     * ファイルの中身が対象の画像でない（AC-39）
     */
    INVALID_IMAGE_FILE(ErrorType.VALIDATION_ERROR, MediaMessage.INVALID_IMAGE_FILE),
    ;

    /**
     * エラーコード
     */
    private final ErrorCode errorCode;

    /**
     * コンストラクタ
     *
     * @param errorType エラータイプ
     * @param message   メッセージ
     */
    MediaErrorCode(ErrorType errorType, I18nMessage message) {
        this.errorCode = ErrorCode.of(this.name(), errorType, message);
    }

    /**
     * エラーコード取得
     *
     * @return {@link ErrorCode}
     */
    @Override
    public ErrorCode getCode() {
        return errorCode;
    }
}

package com.ecsite.module.media.domain;

import com.ecsite.common.error.ErrorCode;
import com.ecsite.common.error.ErrorCodeProvider;
import com.ecsite.common.error.ErrorType;
import lombok.Getter;

/**
 * メディアのエラーコード。<br />
 * コードの文字列は、商品画像 API の契約（admin.yaml）のとおり。商品カタログから移したときに変えていない。
 */
public enum MediaErrorCode implements ErrorCodeProvider {

    /**
     * 画像の形式が対象外（AC-39）
     */
    UNSUPPORTED_IMAGE_FORMAT(ErrorType.VALIDATION_ERROR),
    /**
     * 画像の大きさが範囲の外（0 バイト、または 10MB を超える。AC-39）
     */
    IMAGE_TOO_LARGE(ErrorType.VALIDATION_ERROR),
    /**
     * 受付がない（期限切れで消えたもの、別の範囲のものを含む）
     */
    IMAGE_UPLOAD_NOT_FOUND(ErrorType.VALIDATION_ERROR),
    /**
     * ファイルの中身が対象の画像でない（AC-39）
     */
    INVALID_IMAGE_FILE(ErrorType.VALIDATION_ERROR),
    /**
     * 画像を変換できなかった（壊れたファイル、時間切れ。AC-70）
     */
    IMAGE_CONVERSION_FAILED(ErrorType.VALIDATION_ERROR),
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
    MediaErrorCode(ErrorType errorType, String... paramKeys) {
        this.code = ErrorCodeProvider.errorCode("media", this.name(), errorType, paramKeys);
    }

}

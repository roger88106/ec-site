package com.ecsite.module.media.service;

import com.ecsite.common.message.I18nMessage;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * メディアのメッセージ
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MediaMessage {

    /**
     * 画像の形式は JPEG、PNG、WebP のいずれかにしてください。
     */
    public static final I18nMessage UNSUPPORTED_IMAGE_FORMAT = I18nMessage.of(Code.UNSUPPORTED_IMAGE_FORMAT);

    /**
     * 画像は 1 ファイル 10MB までです。
     */
    public static final I18nMessage IMAGE_TOO_LARGE = I18nMessage.of(Code.IMAGE_TOO_LARGE);

    /**
     * アップロードした画像が見つかりません。もう一度アップロードしてください。
     */
    public static final I18nMessage IMAGE_UPLOAD_NOT_FOUND = I18nMessage.of(Code.IMAGE_UPLOAD_NOT_FOUND);

    /**
     * ファイルの中身が JPEG、PNG、WebP の画像ではありません。
     */
    public static final I18nMessage INVALID_IMAGE_FILE = I18nMessage.of(Code.INVALID_IMAGE_FILE);

    /**
     * メッセージコード
     */
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Code {

        /**
         * 画像の形式は JPEG、PNG、WebP のいずれかにしてください。
         */
        public static final String UNSUPPORTED_IMAGE_FORMAT = "ecsite.media.unsupported_image_format";

        /**
         * 画像は 1 ファイル 10MB までです。
         */
        public static final String IMAGE_TOO_LARGE = "ecsite.media.image_too_large";

        /**
         * アップロードした画像が見つかりません。もう一度アップロードしてください。
         */
        public static final String IMAGE_UPLOAD_NOT_FOUND = "ecsite.media.image_upload_not_found";

        /**
         * ファイルの中身が JPEG、PNG、WebP の画像ではありません。
         */
        public static final String INVALID_IMAGE_FILE = "ecsite.media.invalid_image_file";
    }
}

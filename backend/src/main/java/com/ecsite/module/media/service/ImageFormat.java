package com.ecsite.module.media.service;

import lombok.NonNull;

import java.util.Arrays;
import java.util.Optional;

/**
 * 受け付ける画像の形式（AC-39）。ファイルの先頭のバイト（マジックナンバー）で判定する。<br />
 * ブラウザが申告した形式は信用せず、確定のときに先頭のバイトで確かめる（docs/modules/media.md）。
 */
public enum ImageFormat {

    /**
     * JPEG（FF D8 FF で始まる）
     */
    JPEG("image/jpeg", "jpg"),
    /**
     * PNG（89 50 4E 47 0D 0A 1A 0A で始まる）
     */
    PNG("image/png", "png"),
    /**
     * WebP（"RIFF" + 4 バイトの長さ + "WEBP" で始まる）
     */
    WEBP("image/webp", "webp");

    /**
     * 1 ファイルの大きさの上限（10MB。AC-39）
     */
    public static final long MAX_BYTES = 10L * 1024 * 1024;

    /**
     * 形式の判定に読む先頭の長さ（バイト）
     */
    public static final int SIGNATURE_LENGTH = 12;

    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};

    private static final byte[] WEBP_FOURCC = {'W', 'E', 'B', 'P'};

    private final String contentType;

    private final String extension;

    ImageFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    /**
     * @return MIME タイプ（例：image/jpeg）
     */
    public String contentType() {
        return contentType;
    }

    /**
     * @return 配信のファイルの拡張子（例：jpg）
     */
    public String extension() {
        return extension;
    }

    /**
     * 申告された MIME タイプから形式を引く。
     *
     * @param contentType MIME タイプ
     * @return 形式。受け付けない形式なら空
     */
    public static Optional<ImageFormat> ofContentType(String contentType) {
        return Arrays.stream(values()).filter(format -> format.contentType.equalsIgnoreCase(contentType)).findFirst();
    }

    /**
     * ファイルの先頭のバイトから形式を判定する。
     *
     * @param head ファイルの先頭（{@link #SIGNATURE_LENGTH} バイト以上あれば判定できる）
     * @return 形式。受け付ける形式のどれでもなければ空
     */
    public static Optional<ImageFormat> detect(byte @NonNull [] head) {
        if (startsWith(head, JPEG_SIGNATURE, 0)) {
            return Optional.of(JPEG);
        }
        if (startsWith(head, PNG_SIGNATURE, 0)) {
            return Optional.of(PNG);
        }
        if (startsWith(head, RIFF, 0) && startsWith(head, WEBP_FOURCC, 8)) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] data, byte[] signature, int offset) {
        return data.length >= offset + signature.length
                && Arrays.equals(data, offset, offset + signature.length, signature, 0, signature.length);
    }
}

package com.ecsite.module.media.domain;

import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 受け付ける画像の形式。ファイルの先頭のバイト（マジックナンバー）で判定する。<br />
 * ブラウザが申告した形式は信用せず、確定のときに先頭のバイトで確認する（docs/design/modules/media.md）。
 */
public enum ImageFormat {

    /**
     * JPEG（FF D8 FF で始まる）
     */
    JPEG("JPEG", "jpg", "image/jpeg"),
    /**
     * PNG（89 50 4E 47 0D 0A 1A 0A で始まる）
     */
    PNG("PNG", "png", "image/png"),
    /**
     * WebP（"RIFF" + 4 バイトの長さ + "WEBP" で始まる）
     */
    WEBP("WebP", "webp", "image/webp"),
    /**
     * BMP（"BM" + 4 バイトの大きさ + 0 の予約の 4 バイトで始まる）
     */
    BMP("BMP", "bmp", "image/bmp"),
    /**
     * HEIC / HEIF（ftyp の箱のブランドが HEIC の系統。スマートフォンの写真）
     */
    HEIF("HEIC / HEIF", "heic", "image/heic", "image/heif"),
    /**
     * AVIF（ftyp の箱のブランドが avif・avis）
     */
    AVIF("AVIF", "avif", "image/avif");

    /**
     * 1 ファイルの大きさの上限（10MB）
     */
    public static final long MAX_BYTES = 10L * 1024 * 1024;

    /**
     * 形式の判定に読む先頭の長さ（バイト）。ftyp の箱の互換のブランドを、いくつか読める長さにする
     */
    public static final int SIGNATURE_LENGTH = 32;

    /**
     * JPEG の先頭のバイト（SOI と、次の印の始まり）
     */
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    /**
     * PNG の先頭のバイト（PNG の印）
     */
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    /**
     * RIFF の入れ物の先頭のバイト（WebP の 0〜3 バイト目）
     */
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};

    /**
     * WebP の形式の名前（RIFF の入れ物の 8〜11 バイト目）
     */
    private static final byte[] WEBP_FOURCC = {'W', 'E', 'B', 'P'};

    /**
     * BMP の先頭のバイト
     */
    private static final byte[] BMP_SIGNATURE = {'B', 'M'};

    /**
     * BMP の予約の領域（6〜9 バイト目。必ず 0）
     */
    private static final byte[] BMP_RESERVED = {0, 0, 0, 0};

    /**
     * HEIC・HEIF・AVIF の ftyp の箱の名前（4〜7 バイト目）
     */
    private static final byte[] FTYP = {'f', 't', 'y', 'p'};

    /**
     * HEIC の系統のブランド（HEVC で符号化した画像・画像の列）
     */
    private static final Set<String> HEIF_BRANDS = Set.of("heic", "heix", "heim", "heis", "hevc", "hevx");

    /**
     * AVIF のブランド（画像・画像の列）
     */
    private static final Set<String> AVIF_BRANDS = Set.of("avif", "avis");

    /**
     * HEIF の汎用のブランド。中身の符号化は互換のブランドで分かる
     */
    private static final Set<String> GENERIC_BRANDS = Set.of("mif1", "msf1");

    /**
     * 文に出す形式の名前
     */
    private final String displayName;

    /**
     * ファイルの拡張子
     */
    @Getter
    @Accessors(fluent = true)
    private final String extension;

    /**
     * MIME タイプ（先頭が代表）
     */
    private final List<String> contentTypes;

    /**
     * コンストラクタ
     *
     * @param displayName  文に出す形式の名前
     * @param extension    ファイルの拡張子
     * @param contentTypes MIME タイプ（先頭が代表）
     */
    ImageFormat(String displayName, String extension, String... contentTypes) {
        this.displayName = displayName;
        this.extension = extension;
        this.contentTypes = List.of(contentTypes);
    }

    /**
     * エラーの文に差し込む、受け付ける形式の一覧。
     *
     * @return 形式の名前を読点で区切ったもの（例：JPEG、PNG）
     */
    public static String displayNames() {
        return Arrays.stream(values()).map(format -> format.displayName).collect(Collectors.joining("、"));
    }

    /**
     * @return MIME タイプ（例：image/jpeg）。いくつかあるときは代表のもの
     */
    public String contentType() {
        return contentTypes.getFirst();
    }

    /**
     * 申告された MIME タイプから形式を引く。
     *
     * @param contentType MIME タイプ
     * @return 形式。受け付けない形式なら空
     */
    public static Optional<ImageFormat> ofContentType(String contentType) {
        return Arrays.stream(values())
                .filter(format -> format.contentTypes.stream().anyMatch(type -> type.equalsIgnoreCase(contentType)))
                .findFirst();
    }

    /**
     * ファイルの先頭のバイトから形式を判定する。
     *
     * @param head ファイルの先頭（{@link #SIGNATURE_LENGTH} バイトまで読めば判定できる）
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
        if (startsWith(head, BMP_SIGNATURE, 0) && startsWith(head, BMP_RESERVED, 6)) {
            return Optional.of(BMP);
        }
        if (startsWith(head, FTYP, 4)) {
            return detectFtyp(head);
        }
        return Optional.empty();
    }

// ============================== プライベートメソッド ==============================

    /**
     * ftyp の箱（大きさ 4 バイト、"ftyp"、主のブランド、版 4 バイト、互換のブランド…）から、HEIF と AVIF を分ける。<br />
     * 主のブランドが汎用（mif1、msf1）なら、互換のブランドを見る。どちらとも分からなければ HEIF とする（中身は変換で確認する）。
     */
    private static Optional<ImageFormat> detectFtyp(byte[] head) {
        if (head.length < 12) {
            return Optional.empty();
        }
        var major = brand(head, 8);
        if (AVIF_BRANDS.contains(major)) {
            return Optional.of(AVIF);
        }
        if (HEIF_BRANDS.contains(major)) {
            return Optional.of(HEIF);
        }
        if (!GENERIC_BRANDS.contains(major)) {
            return Optional.empty();
        }
        var boxSize = ((head[0] & 0xFF) << 24) | ((head[1] & 0xFF) << 16) | ((head[2] & 0xFF) << 8) | (head[3] & 0xFF);
        var end = Math.min(boxSize, head.length);
        for (int offset = 16; offset + 4 <= end; offset += 4) {
            if (AVIF_BRANDS.contains(brand(head, offset))) {
                return Optional.of(AVIF);
            }
        }
        return Optional.of(HEIF);
    }

    /**
     * ftyp の箱の、指定した位置の 4 文字のブランドを読む。
     */
    private static String brand(byte[] data, int offset) {
        return new String(data, offset, 4, StandardCharsets.US_ASCII);
    }

    /**
     * 指定した位置から、署名（決まったバイトの並び）が続くかを返す。
     */
    private static boolean startsWith(byte[] data, byte[] signature, int offset) {
        return data.length >= offset + signature.length
                && Arrays.equals(data, offset, offset + signature.length, signature, 0, signature.length);
    }
}

package com.ecsite.module.media.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 受け付ける画像の形式の単体テスト
 */
class ImageFormatTest {

    @Nested
    @DisplayName("申告された MIME タイプ")
    class OfContentType {

        @ParameterizedTest(name = "{0} は {1}")
        @CsvSource({
                "image/jpeg, JPEG",
                "image/png, PNG",
                "image/webp, WEBP",
                "image/bmp, BMP",
                "image/heic, HEIF",
                "image/heif, HEIF",
                "image/avif, AVIF",
                "IMAGE/HEIC, HEIF",
        })
        @DisplayName("AC-67 BMP、HEIC / HEIF、AVIF も受け付ける")
        void acceptsSupportedTypes(String contentType, ImageFormat expected) {
            assertThat(ImageFormat.ofContentType(contentType)).contains(expected);
        }

        @Test
        @DisplayName("対象外の形式は受け付けない")
        void rejectsUnsupportedTypes() {
            assertThat(ImageFormat.ofContentType("image/gif")).isEmpty();
            assertThat(ImageFormat.ofContentType("image/tiff")).isEmpty();
            assertThat(ImageFormat.ofContentType("")).isEmpty();
        }
    }

    @Nested
    @DisplayName("先頭のバイトでの判定")
    class Detect {

        @Test
        @DisplayName("JPEG、PNG、WebP はこれまでどおり判定する")
        void detectsExistingFormats() {
            assertThat(ImageFormat.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0, 1)))
                    .contains(ImageFormat.JPEG);
            assertThat(ImageFormat.detect(bytes(0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13)))
                    .contains(ImageFormat.PNG);
            assertThat(ImageFormat.detect(bytes('R', 'I', 'F', 'F', 36, 0, 0, 0, 'W', 'E', 'B', 'P')))
                    .contains(ImageFormat.WEBP);
        }

        @Test
        @DisplayName("AC-67 BMP は BM と、予約の 4 バイトが 0 であることで判定する")
        void detectsBmp() {
            assertThat(ImageFormat.detect(bytes('B', 'M', 0x36, 0x18, 0, 0, 0, 0, 0, 0, 0x36, 0)))
                    .contains(ImageFormat.BMP);
            // BM で始まるだけの文字列は BMP ではない
            assertThat(ImageFormat.detect("BMW is a car".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        }

        @ParameterizedTest(name = "メジャーブランド {0}")
        @CsvSource({"heic", "heix", "heim", "heis", "hevc", "hevx"})
        @DisplayName("AC-67 ftyp のメジャーブランドが HEIC の系統なら HEIF")
        void detectsHeifByMajorBrand(String brand) {
            assertThat(ImageFormat.detect(ftyp(brand, "mif1", brand))).contains(ImageFormat.HEIF);
        }

        @ParameterizedTest(name = "メジャーブランド {0}")
        @CsvSource({"avif", "avis"})
        @DisplayName("AC-67 ftyp のメジャーブランドが avif・avis なら AVIF")
        void detectsAvifByMajorBrand(String brand) {
            assertThat(ImageFormat.detect(ftyp(brand, "mif1", "miaf"))).contains(ImageFormat.AVIF);
        }

        @Test
        @DisplayName("AC-67 メジャーブランドが汎用（mif1、msf1）なら、互換のブランドで HEIF と AVIF を分ける")
        void detectsByCompatibleBrands() {
            assertThat(ImageFormat.detect(ftyp("mif1", "mif1", "heic"))).contains(ImageFormat.HEIF);
            assertThat(ImageFormat.detect(ftyp("mif1", "mif1", "miaf", "avif"))).contains(ImageFormat.AVIF);
            assertThat(ImageFormat.detect(ftyp("msf1", "msf1", "avis"))).contains(ImageFormat.AVIF);
            // 汎用のブランドだけで、どちらとも分からないものは HEIF として扱う（変換で中身を確認する）
            assertThat(ImageFormat.detect(ftyp("mif1", "mif1"))).contains(ImageFormat.HEIF);
        }

        @Test
        @DisplayName("画像でない ftyp（動画など）、ftyp のないもの、短いものは判定できない")
        void rejectsOthers() {
            assertThat(ImageFormat.detect(ftyp("isom", "isom", "mp41"))).isEmpty();
            assertThat(ImageFormat.detect(ftyp("qt  ", "qt  "))).isEmpty();
            assertThat(ImageFormat.detect(bytes(0, 0, 0, 0x1C, 'f', 't', 'y'))).isEmpty();
            assertThat(ImageFormat.detect(bytes('G', 'I', 'F', '8', '9', 'a', 1, 0, 1, 0, 0, 0))).isEmpty();
            assertThat(ImageFormat.detect(new byte[0])).isEmpty();
        }

        @Test
        @DisplayName("判定に読む長さで、互換のブランドまで読める")
        void signatureLengthCoversCompatibleBrands() {
            var head = ftyp("mif1", "mif1", "miaf", "MA1B", "avif");
            assertThat(head.length).isLessThanOrEqualTo(ImageFormat.SIGNATURE_LENGTH);
            assertThat(ImageFormat.detect(head)).contains(ImageFormat.AVIF);
        }
    }

    // ============================== 補助 ==============================

    /**
     * int で書いた値を、バイト列にする（0x80 以上を書きやすくするため）
     */
    private static byte[] bytes(int... values) {
        var result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

    /**
     * ISO の基本メディアファイルの ftyp ボックス（大きさ、"ftyp"、メジャーブランド、バージョン、互換のブランド）
     */
    private static byte[] ftyp(String majorBrand, String... compatibleBrands) {
        var out = new ByteArrayOutputStream();
        var size = 16 + 4 * compatibleBrands.length;
        out.writeBytes(new byte[]{0, 0, 0, (byte) size});
        out.writeBytes("ftyp".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(majorBrand.getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(new byte[]{0, 0, 0, 0});
        for (var brand : compatibleBrands) {
            out.writeBytes(brand.getBytes(StandardCharsets.US_ASCII));
        }
        return out.toByteArray();
    }
}

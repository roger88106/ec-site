package com.ecsite.module.media.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 受け付ける画像の形式の単体テスト
 *
 * <p>申告された MIME タイプからの引き当て（{@link ImageFormat#ofContentType}）と、先頭のバイトでの判定
 * （{@link ImageFormat#detect}）を確認する。
 * API を通した受付と用意は {@code MediaApiTest} が確認する。
 */
class ImageFormatTest {

    // ============================== テスト ==============================

    @Nested
    @DisplayName("申告された MIME タイプ")
    class OfContentType {

        @ParameterizedTest(name = "「{0}」は {1}")
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
        @DisplayName("AC-39、67 受け付ける形式の MIME タイプなら、その形式になる")
        void acceptsSupportedTypes(String contentType, ImageFormat expected) {
            // 操作と確認：申告の MIME タイプ（大文字と小文字は区別しない）から形式を引ける
            assertThat(ImageFormat.ofContentType(contentType)).contains(expected);
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"image/gif", "image/tiff", ""})
        @DisplayName("AC-39 対象外の MIME タイプは受け付けない")
        void rejectsUnsupportedTypes(String contentType) {
            // 操作と確認：形式を引けない
            assertThat(ImageFormat.ofContentType(contentType)).isEmpty();
        }
    }

    @Nested
    @DisplayName("先頭のバイトでの判定")
    class Detect {

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.ecsite.module.media.domain.ImageFormatTest#basicFormats")
        @DisplayName("AC-39 基本の形式の印で始まれば、その形式と判定する")
        void detectsBasicFormats(ImageFormat expected, byte[] head) {
            // 操作と確認：先頭の印から形式を判定できる
            assertThat(ImageFormat.detect(head)).contains(expected);
        }

        @Test
        @DisplayName("AC-67 BM で始まり、予約の 4 バイトが 0 なら BMP と判定する")
        void detectsBmp() {
            // 準備：BM と大きさの後に、0 の予約の 4 バイトが続く
            var head = bytes('B', 'M', 0x36, 0x18, 0, 0, 0, 0, 0, 0, 0x36, 0);

            // 操作と確認：BMP と判定する
            assertThat(ImageFormat.detect(head)).contains(ImageFormat.BMP);
        }

        @Test
        @DisplayName("AC-67 BM で始まるだけで予約の 4 バイトが 0 でなければ、BMP と判定しない")
        void rejectsTextStartingWithBm() {
            // 準備：BM で始まる文字列
            var head = "BMW is a car".getBytes(StandardCharsets.US_ASCII);

            // 操作と確認：どの形式とも判定しない
            assertThat(ImageFormat.detect(head)).isEmpty();
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"heic", "heix", "heim", "heis", "hevc", "hevx"})
        @DisplayName("AC-67 ftyp のメジャーブランドが HEIC の系統なら HEIF と判定する")
        void detectsHeifByMajorBrand(String brand) {
            // 操作と確認：HEIF と判定する
            assertThat(ImageFormat.detect(ftyp(brand, "mif1", brand))).contains(ImageFormat.HEIF);
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"avif", "avis"})
        @DisplayName("AC-67 ftyp のメジャーブランドが AVIF のブランドなら AVIF と判定する")
        void detectsAvifByMajorBrand(String brand) {
            // 操作と確認：AVIF と判定する
            assertThat(ImageFormat.detect(ftyp(brand, "mif1", "miaf"))).contains(ImageFormat.AVIF);
        }

        @Test
        @DisplayName("AC-67 メジャーブランドが汎用で、互換のブランドに HEIC の系統があれば HEIF と判定する")
        void detectsHeifByCompatibleBrand() {
            // 操作と確認：HEIF と判定する
            assertThat(ImageFormat.detect(ftyp("mif1", "mif1", "heic"))).contains(ImageFormat.HEIF);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.ecsite.module.media.domain.ImageFormatTest#genericWithAvifBrand")
        @DisplayName("AC-67 メジャーブランドが汎用で、互換のブランドに AVIF のブランドがあれば AVIF と判定する")
        void detectsAvifByCompatibleBrand(String name, byte[] head) {
            // 操作と確認：AVIF と判定する
            assertThat(ImageFormat.detect(head)).contains(ImageFormat.AVIF);
        }

        @Test
        @DisplayName("AC-67 汎用のブランドだけで中身が分からなければ、HEIF として扱う")
        void treatsGenericOnlyAsHeif() {
            // 操作と確認：HEIF と判定する（中身は変換で確認する）
            assertThat(ImageFormat.detect(ftyp("mif1", "mif1"))).contains(ImageFormat.HEIF);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.ecsite.module.media.domain.ImageFormatTest#undetectableHeads")
        @DisplayName("AC-39 受け付ける形式のどの印にも当たらなければ、判定できない")
        void rejectsOthers(String name, byte[] head) {
            // 操作と確認：どの形式とも判定しない
            assertThat(ImageFormat.detect(head)).isEmpty();
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(ints = {8, 11})
        @DisplayName("AC-67 ftyp まであってもメジャーブランドの途中で切れていれば（12 バイト未満）、判定できない")
        void rejectsFtypShorterThanMajorBrand(int length) {
            // 準備：HEIC の ftyp を、メジャーブランドの途中で切ったもの
            var head = Arrays.copyOf(ftyp("heic", "mif1", "heic"), length);

            // 操作と確認：どの形式とも判定しない（範囲外の読み取りの例外にならない）
            assertThat(ImageFormat.detect(head)).isEmpty();
        }

        @Test
        @DisplayName("AC-67 ftyp のメジャーブランドまでちょうど 12 バイトあれば、メジャーブランドで判定する")
        void detectsFtypWithMajorBrandOnly() {
            // 準備：HEIC の ftyp の、メジャーブランドまでの 12 バイト
            var head = Arrays.copyOf(ftyp("heic", "mif1", "heic"), 12);

            // 操作と確認：HEIF と判定する
            assertThat(ImageFormat.detect(head)).contains(ImageFormat.HEIF);
        }

        @Test
        @DisplayName("判定に読む長さで、互換のブランドまで読める")
        void signatureLengthCoversCompatibleBrands() {
            // 準備：AVIF のブランドが互換のブランドの 3 つ目にある ftyp
            var head = ftyp("mif1", "mif1", "miaf", "MA1B", "avif");

            // 確認（前提）：判定に読む長さに収まる
            assertThat(head.length).isLessThanOrEqualTo(ImageFormat.SIGNATURE_LENGTH);

            // 操作と確認：3 つ目の互換のブランドで AVIF と判定する
            assertThat(ImageFormat.detect(head)).contains(ImageFormat.AVIF);
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * JPEG・PNG・WebP の、先頭のバイトを返す。
     *
     * @return 形式と、その形式の先頭のバイト
     */
    static Stream<Arguments> basicFormats() {
        return Stream.of(
                Arguments.of(ImageFormat.JPEG, bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0, 1)),
                Arguments.of(ImageFormat.PNG, bytes(0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13)),
                Arguments.of(ImageFormat.WEBP, bytes('R', 'I', 'F', 'F', 36, 0, 0, 0, 'W', 'E', 'B', 'P')));
    }

    /**
     * メジャーブランドが汎用で、互換のブランドに AVIF のブランドがある ftyp を返す。
     *
     * @return 場合の名前と、ftyp のボックス
     */
    static Stream<Arguments> genericWithAvifBrand() {
        return Stream.of(
                Arguments.of("mif1 に avif", ftyp("mif1", "mif1", "miaf", "avif")),
                Arguments.of("msf1 に avis", ftyp("msf1", "msf1", "avis")));
    }

    /**
     * どの形式とも判定できない先頭のバイトを返す。
     *
     * @return 場合の名前と、先頭のバイト
     */
    static Stream<Arguments> undetectableHeads() {
        return Stream.of(
                Arguments.of("動画の ftyp（isom）", ftyp("isom", "isom", "mp41")),
                Arguments.of("QuickTime の ftyp", ftyp("qt  ", "qt  ")),
                Arguments.of("ftyp の途中で切れたもの", bytes(0, 0, 0, 0x1C, 'f', 't', 'y')),
                Arguments.of("GIF", bytes('G', 'I', 'F', '8', '9', 'a', 1, 0, 1, 0, 0, 0)),
                Arguments.of("空", new byte[0]));
    }

    /**
     * int で書いた値を、バイト列にする。
     *
     * <p>0x80 以上の値を、キャストなしで書けるようにするため。
     *
     * @param values 値
     * @return バイト列
     */
    private static byte[] bytes(int... values) {
        var result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

    /**
     * ISO の基本メディアファイルの ftyp ボックスを作る。
     *
     * <p>大きさ、"ftyp"、メジャーブランド、バージョン、互換のブランドの順に並べる。
     *
     * @param majorBrand       メジャーブランド
     * @param compatibleBrands 互換のブランド
     * @return ftyp のボックス
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

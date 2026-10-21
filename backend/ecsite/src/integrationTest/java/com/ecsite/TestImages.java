package com.ecsite;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * テスト用の画像（{@code resources/images}。作り方は {@code generate.sh}）。<br />
 * 左が赤、右が青の 64x32。JPEG・HEIC・AVIF には、向き（時計回りに 90 度）、位置情報、撮影機器（TestCam）が付いている。
 * 確定のときに imgproxy で変換するので、受付から登録までを通すテストは本物の画像を使う。
 */
public final class TestImages {

    private TestImages() {
    }

    /**
     * @return PNG
     */
    public static byte[] png() {
        return of("source.png");
    }

    /**
     * @return JPEG（向き、位置情報、撮影機器付き）
     */
    public static byte[] jpeg() {
        return of("source.jpg");
    }

    /**
     * テスト用の画像を読む。
     *
     * @param file ファイル名（例：source.heic）
     * @return ファイルの中身
     */
    public static byte[] of(String file) {
        try (var in = TestImages.class.getResourceAsStream("/images/" + file)) {
            if (in == null) {
                throw new IllegalArgumentException("テスト用の画像がない：" + file);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

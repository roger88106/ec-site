package com.ecsite.module.media.infrastructure.converter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 画像の変換の設定（ImgproxyProperties）の文字列表現のテスト
 *
 * <p>ログに出ても秘密の値が漏れないことを確認する。
 */
class ImgproxyPropertiesTest {

    // ============================== 定数 ==============================

    /**
     * imgproxy の URL
     */
    private static final String BASE_URL = "http://imgproxy.internal:8080";

    /**
     * 署名の鍵
     */
    private static final String KEY = "aabbccddeeff0011";

    /**
     * 署名のソルト
     */
    private static final String SALT = "1122334455667788";

    /**
     * WebP の品質
     */
    private static final int QUALITY = 82;

    /**
     * 確認する設定
     */
    private final ImgproxyProperties properties = new ImgproxyProperties(
            URI.create(BASE_URL), KEY, SALT, QUALITY, Duration.ofSeconds(30));

    // ============================== テスト ==============================

    @Test
    @DisplayName("文字列表現に、署名の鍵とソルトを含まない")
    void toStringHidesSecrets() {
        // 操作と確認：秘密の値が出ない
        assertThat(properties.toString())
                .doesNotContain(KEY)
                .doesNotContain(SALT);
    }

    @Test
    @DisplayName("文字列表現に、秘密でない値（URL など）を含む")
    void toStringKeepsNonSecrets() {
        // 操作と確認：URL と品質が出る
        assertThat(properties.toString())
                .contains(BASE_URL)
                .contains(String.valueOf(QUALITY));
    }
}

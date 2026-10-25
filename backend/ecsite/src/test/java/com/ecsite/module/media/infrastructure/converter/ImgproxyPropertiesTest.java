package com.ecsite.module.media.infrastructure.converter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 画像の変換の設定の文字列表現（spec 182 DC-182-7）。<br />
 * ログに出ても秘密の値が漏れないことを確認する。
 */
class ImgproxyPropertiesTest {

    private final ImgproxyProperties properties = new ImgproxyProperties(
            URI.create("http://imgproxy.internal:8080"), "aabbccddeeff0011", "1122334455667788", 82,
            Duration.ofSeconds(30));

    @Test
    @DisplayName("文字列表現に、署名の鍵とソルトを含まない")
    void toStringHidesSecrets() {
        assertThat(properties.toString())
                .doesNotContain("aabbccddeeff0011")
                .doesNotContain("1122334455667788");
    }

    @Test
    @DisplayName("文字列表現に、秘密でない値（URL など）を含む")
    void toStringKeepsNonSecrets() {
        assertThat(properties.toString())
                .contains("http://imgproxy.internal:8080")
                .contains("82");
    }
}

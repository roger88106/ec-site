package com.ecsite.module.media.infrastructure.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 画像の保存先の設定の文字列表現。<br />
 * ログに出ても秘密の値が漏れないことを確認する。
 */
class MediaStoragePropertiesTest {

    private final MediaStorageProperties properties = new MediaStorageProperties(
            URI.create("http://seaweedfs:8333"), "ap-northeast-1", true,
            "test-access-key-id", "test-secret-access-key",
            "incoming-bucket", "public-bucket", URI.create("http://cdn.example.test"),
            Duration.ofMinutes(5), Duration.ofSeconds(10), Duration.ofSeconds(30));

    @Test
    @DisplayName("文字列表現に、アクセスキー ID と秘密の鍵を含まない")
    void toStringHidesSecrets() {
        assertThat(properties.toString())
                .doesNotContain("test-access-key-id")
                .doesNotContain("test-secret-access-key");
    }

    @Test
    @DisplayName("資格情報が null（本番の形）でも、文字列表現を作れる")
    void toStringAcceptsNullCredentials() {
        var withoutCredentials = new MediaStorageProperties(
                URI.create("http://seaweedfs:8333"), "ap-northeast-1", true,
                null, null,
                "incoming-bucket", "public-bucket", URI.create("http://cdn.example.test"),
                Duration.ofMinutes(5), Duration.ofSeconds(10), Duration.ofSeconds(30));

        assertThat(withoutCredentials.toString())
                .contains("accessKeyId=null")
                .contains("secretAccessKey=null");
    }

    @Test
    @DisplayName("文字列表現に、秘密でない値（バケット名など）を含む")
    void toStringKeepsNonSecrets() {
        assertThat(properties.toString())
                .contains("incoming-bucket")
                .contains("public-bucket")
                .contains("ap-northeast-1");
    }
}

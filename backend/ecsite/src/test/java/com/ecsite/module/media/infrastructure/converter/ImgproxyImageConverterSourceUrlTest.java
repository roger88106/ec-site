package com.ecsite.module.media.infrastructure.converter;

import com.ecsite.module.media.infrastructure.storage.MediaStorageProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * imgproxy の画像の変換の、変換元の場所のテスト
 */
class ImgproxyImageConverterSourceUrlTest {

    @Test
    @DisplayName("変換元の場所は、アップロードのバケットとキーを指す（配信のバケットではない）")
    void pointsToUploadedFileInIncomingBucket() {
        var storageProperties = new MediaStorageProperties(null, "us-east-1", true, null, null,
                "incoming-x", "public-y", URI.create("http://cdn.test"), Duration.ofMinutes(5),
                Duration.ofSeconds(10), Duration.ofSeconds(30));
        var properties = new ImgproxyProperties(URI.create("http://imgproxy.test"), "0123456789abcdef",
                "fedcba9876543210", 82, Duration.ofSeconds(5));
        var converter = new ImgproxyImageConverter(RestClient.create(), properties, storageProperties);

        assertThat(converter.uploadSourceUrl("scope/upload")).isEqualTo("s3://incoming-x/scope/upload");
    }
}

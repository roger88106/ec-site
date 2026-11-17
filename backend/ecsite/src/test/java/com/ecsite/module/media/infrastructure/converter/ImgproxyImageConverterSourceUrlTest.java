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

    // ============================== 定数 ==============================

    /**
     * アップロードのバケット
     */
    private static final String INCOMING_BUCKET = "incoming-x";

    /**
     * 配信のバケット
     *
     * <p>アップロードのバケットと違う名前にし、取り違えれば分かるようにする。
     */
    private static final String PUBLIC_BUCKET = "public-y";

    /**
     * 変換するファイルの、アップロードのバケットのキー
     */
    private static final String UPLOAD_KEY = "scope/upload";

    // ============================== テスト ==============================

    @Test
    @DisplayName("変換元の場所は、アップロードのバケットとキーを指す（配信のバケットではない）")
    void pointsToUploadedFileInIncomingBucket() {
        // 準備：アップロードと配信のバケットの名前が違う設定の変換
        var storageProperties = new MediaStorageProperties(null, "us-east-1", true, null, null,
                INCOMING_BUCKET, PUBLIC_BUCKET, URI.create("http://cdn.test"), Duration.ofMinutes(5),
                Duration.ofSeconds(10), Duration.ofSeconds(30));
        var properties = new ImgproxyProperties(URI.create("http://imgproxy.test"), "0123456789abcdef",
                "fedcba9876543210", 82, Duration.ofSeconds(5));
        var converter = new ImgproxyImageConverter(RestClient.create(), properties, storageProperties);

        // 操作と確認：アップロードのバケットのキーを指す
        assertThat(converter.uploadSourceUrl(UPLOAD_KEY)).isEqualTo("s3://" + INCOMING_BUCKET + "/" + UPLOAD_KEY);
    }
}

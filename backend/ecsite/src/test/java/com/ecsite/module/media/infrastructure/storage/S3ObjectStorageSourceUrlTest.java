package com.ecsite.module.media.infrastructure.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S3 のファイルの置き場所の、変換元の場所のテスト
 */
class S3ObjectStorageSourceUrlTest {

    @Test
    @DisplayName("変換元の場所は、アップロードのバケットとキーを指す（配信のバケットではない）")
    void pointsToUploadedFileInIncomingBucket() {
        var properties = new MediaStorageProperties(null, "us-east-1", true, null, null,
                "incoming-x", "public-y", URI.create("http://cdn.test"), Duration.ofMinutes(5),
                Duration.ofSeconds(10), Duration.ofSeconds(30));
        var storage = new S3ObjectStorage(null, null, properties);

        assertThat(storage.uploadSourceUrl("scope/upload")).isEqualTo("s3://incoming-x/scope/upload");
    }
}

package com.ecsite.module.media.infrastructure.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ファイルの置き場所の S3 の実装の、変換元の場所のテスト（docs/03-機能仕様/media.md「アップロードの流れ」）。
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

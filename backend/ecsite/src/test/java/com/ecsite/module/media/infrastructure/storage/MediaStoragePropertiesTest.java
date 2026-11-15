package com.ecsite.module.media.infrastructure.storage;

import com.ecsite.TestSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ファイルの置き場所の設定（MediaStorageProperties）の単体テスト
 *
 * <p>文字列表現は、ログに出ても秘密の値が漏れないことを確認する。呼び出しの時間の上限は、Spring を起動せずに、
 * 設定の型へ値を当てはめて確認する（上限で実際に止まることは {@code S3ObjectStorageFailureTest} が確認する）。
 */
class MediaStoragePropertiesTest {

    // ============================== 定数 ==============================

    /**
     * 設定の接頭辞
     */
    private static final String PREFIX = "ecsite.media.storage";

    /**
     * リージョン
     */
    private static final String REGION = "ap-northeast-1";

    /**
     * アクセスキー ID
     */
    private static final String ACCESS_KEY_ID = "test-access-key-id";

    /**
     * 秘密の鍵
     */
    private static final String SECRET_ACCESS_KEY = "test-secret-access-key";

    /**
     * アップロードのバケット
     */
    private static final String INCOMING_BUCKET = "incoming-bucket";

    /**
     * 配信のバケット
     */
    private static final String PUBLIC_BUCKET = "public-bucket";

    /**
     * 配信の基準 URL
     */
    private static final String PUBLIC_BASE_URL = "http://cdn.example.test";

    /**
     * 資格情報を持つ設定
     */
    private final MediaStorageProperties properties = properties(ACCESS_KEY_ID, SECRET_ACCESS_KEY);

    // ============================== テスト ==============================

    @Nested
    @DisplayName("文字列表現")
    class StringForm {

        @Test
        @DisplayName("文字列表現に、アクセスキー ID と秘密の鍵を含まない")
        void toStringHidesSecrets() {
            // 操作と確認：資格情報の値が出ない
            assertThat(properties.toString())
                    .doesNotContain(ACCESS_KEY_ID)
                    .doesNotContain(SECRET_ACCESS_KEY);
        }

        @Test
        @DisplayName("資格情報が null（本番の形）でも、文字列表現を作れる")
        void toStringAcceptsNullCredentials() {
            // 準備：資格情報のない設定
            var withoutCredentials = properties(null, null);

            // 操作と確認：資格情報は null と出る
            assertThat(withoutCredentials.toString())
                    .contains("accessKeyId=null")
                    .contains("secretAccessKey=null");
        }

        @Test
        @DisplayName("文字列表現に、秘密でない値（バケット名など）を含む")
        void toStringKeepsNonSecrets() {
            // 操作と確認：バケット名とリージョンが出る
            assertThat(properties.toString())
                    .contains(INCOMING_BUCKET)
                    .contains(PUBLIC_BUCKET)
                    .contains(REGION);
        }
    }

    @Nested
    @DisplayName("呼び出しの時間の上限")
    class Timeouts {

        @Test
        @DisplayName("AC-301 S3 の呼び出しは、既定で 1 回の試行 10 秒・全体 30 秒の上限で呼ぶ")
        void defaultTimeouts() {
            // 操作：上限を指定せずに設定を読む
            var bound = bind(Map.of());

            // 確認：環境変数の手順書の既定値
            assertThat(bound.attemptTimeout()).isEqualTo(Duration.ofSeconds(10));
            assertThat(bound.callTimeout()).isEqualTo(Duration.ofSeconds(30));
        }

        @Test
        @DisplayName("AC-301 試行と全体の上限は、設定で変えられる")
        void overridesTimeouts() {
            // 操作：上限を指定して設定を読む
            var bound = bind(Map.of("attempt-timeout", "2s", "call-timeout", "45s"));

            // 確認：指定した値になる
            assertThat(bound.attemptTimeout()).isEqualTo(Duration.ofSeconds(2));
            assertThat(bound.callTimeout()).isEqualTo(Duration.ofSeconds(45));
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 定数の値で、資格情報を指定した設定を作る。
     *
     * @param accessKeyId     アクセスキー ID
     * @param secretAccessKey 秘密の鍵
     * @return 設定
     */
    private static MediaStorageProperties properties(String accessKeyId, String secretAccessKey) {
        return new MediaStorageProperties(
                URI.create("http://seaweedfs:8333"), REGION, true,
                accessKeyId, secretAccessKey,
                INCOMING_BUCKET, PUBLIC_BUCKET, URI.create(PUBLIC_BASE_URL),
                Duration.ofMinutes(5), Duration.ofSeconds(10), Duration.ofSeconds(30));
    }

    /**
     * 接頭辞の下の値を、必須の値と合わせて設定の型に当てはめる。
     *
     * @param values 必須の値に加える値
     * @return 設定
     */
    private static MediaStorageProperties bind(Map<String, String> values) {
        var all = new HashMap<>(values);
        all.put("region", REGION);
        all.put("incoming-bucket", INCOMING_BUCKET);
        all.put("public-bucket", PUBLIC_BUCKET);
        all.put("public-base-url", PUBLIC_BASE_URL);
        return TestSettings.bind(PREFIX, MediaStorageProperties.class, all);
    }
}

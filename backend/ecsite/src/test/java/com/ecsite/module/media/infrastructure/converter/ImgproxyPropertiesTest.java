package com.ecsite.module.media.infrastructure.converter;

import com.ecsite.support.TestSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.BindException;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 画像の変換の設定（ImgproxyProperties）のテスト
 *
 * <p>ログに出ても秘密の値が漏れないことと、品質の範囲（1〜100）の外は値の検証で拒否されることを確認する。
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

    @Nested
    @DisplayName("品質の範囲")
    class QualityRange {

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(ints = {1, 100})
        @DisplayName("品質が範囲の端なら、値の検証を通り、その品質になる")
        void acceptsQualityAtBounds(int quality) {
            // 操作：範囲の端の品質を検証付きで当てはめる
            var properties = TestSettings.bindValidated("ecsite.media.converter", ImgproxyProperties.class,
                    Map.of("base-url", BASE_URL, "key", KEY, "salt", SALT, "quality", String.valueOf(quality)));

            // 確認：設定した品質を持つ
            assertThat(properties.quality()).isEqualTo(quality);
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(ints = {0, 101})
        @DisplayName("品質が範囲の外なら、値の検証で拒否され、quality が知らされる")
        void rejectsQualityOutOfBounds(int quality) {
            // 操作と確認：値の検証で拒否され、原因に設定の名前がある
            assertThatThrownBy(() -> TestSettings.bindValidated("ecsite.media.converter", ImgproxyProperties.class,
                    Map.of("base-url", BASE_URL, "key", KEY, "salt", SALT, "quality", String.valueOf(quality))))
                    .isInstanceOf(BindException.class)
                    .rootCause().hasMessageContaining("quality");
        }
    }

    @ParameterizedTest(name = "「{0}」")
    @ValueSource(strings = {"0s", "-1s", "999us"})
    @DisplayName("変換の呼び出しを待つ時間が 0、負の値、下限の 1ms 未満（999us）なら、値の検証で拒否される")
    void rejectsTimeoutBelowMinimum(String value) {
        // 操作と確認：必須の値と許す範囲の外の時間を渡すと、値の検証で拒否される
        assertThatThrownBy(() -> TestSettings.bindValidated("ecsite.media.converter", ImgproxyProperties.class,
                Map.of("base-url", BASE_URL, "key", KEY, "salt", SALT, "timeout", value)))
                .isInstanceOf(BindException.class);
    }

    @Test
    @DisplayName("変換の呼び出しを待つ時間が下限の 1ms なら、値の検証を通り、その値になる")
    void acceptsTimeoutAtMinimum() {
        // 操作：1ms を検証付きで当てはめる
        var properties = TestSettings.bindValidated("ecsite.media.converter", ImgproxyProperties.class,
                Map.of("base-url", BASE_URL, "key", KEY, "salt", SALT, "timeout", "1ms"));

        // 確認：1ms になる
        assertThat(properties.timeout()).isEqualTo(Duration.ofMillis(1));
    }
}

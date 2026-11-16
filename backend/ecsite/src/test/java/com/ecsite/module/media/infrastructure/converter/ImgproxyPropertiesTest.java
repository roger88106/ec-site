package com.ecsite.module.media.infrastructure.converter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 画像の変換の設定（ImgproxyProperties）のテスト
 *
 * <p>ログに出ても秘密の値が漏れないことと、品質の範囲（1〜100）の外では起動に失敗することを確認する。
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

        /**
         * 変換の設定だけを読む Spring のコンテキスト（必須の URL・鍵・ソルトを設定済み）
         */
        private final ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
                .withUserConfiguration(Converter.class)
                .withPropertyValues("ecsite.media.converter.base-url=" + BASE_URL,
                        "ecsite.media.converter.key=" + KEY, "ecsite.media.converter.salt=" + SALT);

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(ints = {1, 100})
        @DisplayName("品質が下限の 1 か上限の 100 なら、起動でき、その品質になる")
        void startsWithQualityAtBounds(int quality) {
            // 操作と確認：起動でき、設定した品質を持つ
            runner.withPropertyValues("ecsite.media.converter.quality=" + quality)
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context.getBean(ImgproxyProperties.class).quality()).isEqualTo(quality);
                    });
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(ints = {0, 101})
        @DisplayName("品質が 1〜100 の外なら、起動に失敗し、quality が知らされる")
        void failsWithQualityOutOfBounds(int quality) {
            // 操作と確認：起動に失敗し、原因に設定の名前がある
            runner.withPropertyValues("ecsite.media.converter.quality=" + quality)
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("quality");
                    });
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 変換の設定を有効にする
     */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ImgproxyProperties.class)
    static class Converter {
    }
}

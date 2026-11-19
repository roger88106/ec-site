package com.ecsite.module.member.application;

import com.ecsite.support.TestSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.BindException;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 会員 ID のキャッシュの設定の単体テスト
 *
 * <p>設定は Spring を起動せずに確認する。既定は、設定ファイル（{@code application.yml}）の値を含めて、起動したアプリが使う値を確認する。
 */
class MemberIdCachePropertiesTest {

    // ============================== 定数 ==============================

    /**
     * 会員 ID のキャッシュの設定の接頭辞
     */
    private static final String PREFIX = "ecsite.member.id-cache";

    // ============================== テスト ==============================

    @Nested
    @DisplayName("既定の値")
    class Defaults {

        @Test
        @DisplayName("DC-557-3 件数の上限は 10,000、期限は 1 時間")
        void defaults() {
            // 操作：設定ファイルを読んで当てはめる
            var properties = TestSettings.loadAndBind(PREFIX, MemberIdCacheProperties.class);

            // 確認：既定の値
            assertThat(properties.maximumSize()).isEqualTo(10_000);
            assertThat(properties.expireAfterWrite()).isEqualTo(Duration.ofHours(1));
        }
    }

    @Nested
    @DisplayName("設定で変える")
    class Overrides {

        @Test
        @DisplayName("DC-557-3 件数の上限と期限は、設定で変えられる")
        void overrides() {
            // 操作：件数の上限と期限を渡して当てはめる
            var properties = TestSettings.bind(PREFIX, MemberIdCacheProperties.class,
                    Map.of("maximum-size", "500", "expire-after-write", "10m"));

            // 確認：渡した値になる
            assertThat(properties.maximumSize()).isEqualTo(500);
            assertThat(properties.expireAfterWrite()).isEqualTo(Duration.ofMinutes(10));
        }
    }

    @Nested
    @DisplayName("件数の上限の下限")
    class MaximumSizeMinimum {

        @Test
        @DisplayName("件数の上限が下限の 1 なら、検証を通り、1 件になる")
        void acceptsMinimum() {
            // 操作：1 件を検証付きで当てはめる
            var properties = TestSettings.bindValidated(PREFIX, MemberIdCacheProperties.class,
                    Map.of("maximum-size", "1"));

            // 確認：1 件
            assertThat(properties.maximumSize()).isEqualTo(1);
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"0", "-1"})
        @DisplayName("件数の上限が下限より 1 小さい 0 か負なら、値の検証で拒否される")
        void rejectsBelowMinimum(String value) {
            // 操作と確認：値の検証で拒否される
            assertThatThrownBy(() -> TestSettings.bindValidated(PREFIX, MemberIdCacheProperties.class,
                    Map.of("maximum-size", value))).isInstanceOf(BindException.class);
        }
    }

    @Nested
    @DisplayName("期限の下限")
    class ExpirationMinimum {

        @Test
        @DisplayName("期限が下限の 1ms なら、検証を通り、その値になる")
        void acceptsMinimum() {
            // 操作：1ms を検証付きで当てはめる
            var properties = TestSettings.bindValidated(PREFIX, MemberIdCacheProperties.class,
                    Map.of("expire-after-write", "1ms"));

            // 確認：1ms になる
            assertThat(properties.expireAfterWrite()).isEqualTo(Duration.ofMillis(1));
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"0s", "-1s", "999us"})
        @DisplayName("期限が 0、負の値、下限の 1ms 未満（999us）なら、値の検証で拒否される")
        void rejectsBelowMinimum(String value) {
            // 操作と確認：値の検証で拒否される
            assertThatThrownBy(() -> TestSettings.bindValidated(PREFIX, MemberIdCacheProperties.class,
                    Map.of("expire-after-write", value))).isInstanceOf(BindException.class);
        }
    }
}

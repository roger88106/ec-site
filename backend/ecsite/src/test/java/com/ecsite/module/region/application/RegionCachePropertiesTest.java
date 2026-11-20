package com.ecsite.module.region.application;

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
 * 地域のキャッシュの設定の単体テスト
 *
 * <p>設定は Spring を起動せずに確認する。既定は、設定ファイル（{@code application.yml}）の値を含めて、起動したアプリが使う値を確認する。
 */
class RegionCachePropertiesTest {

    // ============================== 定数 ==============================

    /**
     * 地域のキャッシュの設定の接頭辞
     */
    private static final String PREFIX = "ecsite.region.cache";

    // ============================== テスト ==============================

    @Nested
    @DisplayName("既定の値")
    class Defaults {

        @Test
        @DisplayName("期限は 24 時間")
        void defaults() {
            // 操作：設定ファイルを読んで当てはめる
            var properties = TestSettings.loadAndBind(PREFIX, RegionCacheProperties.class);

            // 確認：既定の値
            assertThat(properties.expireAfterWrite()).isEqualTo(Duration.ofHours(24));
        }
    }

    @Nested
    @DisplayName("設定で変える")
    class Overrides {

        @Test
        @DisplayName("期限は、設定で変えられる")
        void overrides() {
            // 操作：期限を渡して当てはめる
            var properties = TestSettings.bind(PREFIX, RegionCacheProperties.class,
                    Map.of("expire-after-write", "10m"));

            // 確認：渡した値になる
            assertThat(properties.expireAfterWrite()).isEqualTo(Duration.ofMinutes(10));
        }
    }

    @Nested
    @DisplayName("期限の下限")
    class ExpirationMinimum {

        @Test
        @DisplayName("期限が下限の 1ms なら、検証を通り、その値になる")
        void acceptsMinimum() {
            // 操作：1ms を検証付きで当てはめる
            var properties = TestSettings.bindValidated(PREFIX, RegionCacheProperties.class,
                    Map.of("expire-after-write", "1ms"));

            // 確認：1ms になる
            assertThat(properties.expireAfterWrite()).isEqualTo(Duration.ofMillis(1));
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"0s", "-1s", "999us"})
        @DisplayName("期限が 0、負の値、下限の 1ms 未満（999us）なら、値の検証で拒否される")
        void rejectsBelowMinimum(String value) {
            // 操作と確認：値の検証で拒否される
            assertThatThrownBy(() -> TestSettings.bindValidated(PREFIX, RegionCacheProperties.class,
                    Map.of("expire-after-write", value))).isInstanceOf(BindException.class);
        }
    }
}

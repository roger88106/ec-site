package com.ecsite.config;

import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 現在時刻（{@link Clock}）の検証
 *
 * <p>バックエンドのアーキテクチャの「水平スケール」に対応する。
 */
class ClockConfigTest {

    // ============================== 定数 ==============================

    /**
     * テストから決める時刻
     *
     * <p>日本時間の 2026-11-01 09:00。統合テストの時計の初期値と違う時刻にする。
     */
    private static final Instant TIME = Instant.parse("2026-11-01T00:00:00Z");

    // ============================== テスト ==============================

    /**
     * 本番の設定
     *
     * <p>統合テストの共通設定はアプリの時計を差し替えるので、本番の {@link ClockConfig} だけを読む。
     */
    @Nested
    @SpringJUnitConfig(ClockConfig.class)
    @DisplayName("通常の構成")
    class Default {

        // ============================== Bean ==============================

        /**
         * 注入される時計
         */
        @Autowired
        Clock clock;

        @Test
        @DisplayName("UTC のシステム時計が注入される")
        void systemClockInUtc() {
            // 確認：時間帯が UTC で、今の時刻を指す
            assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
            assertThat(clock.instant()).isCloseTo(Instant.now(), within(Duration.ofSeconds(5)));
        }
    }

    @Nested
    @IntegrationTest
    @DisplayName("固定の時計に差し替えた構成")
    class Fixed {

        // ============================== Bean ==============================

        /**
         * 注入される時計
         */
        @Autowired
        Clock clock;

        /**
         * テストから操作する時計
         */
        @Autowired
        MutableClock testClock;

        @Test
        @DisplayName("注入される時計を、テストから決めた時刻にできる")
        void setsInstant() {
            // 操作：テストの時計を決めた時刻にする
            testClock.setInstant(TIME);

            // 確認：注入される時計がその時刻になる
            assertThat(clock.instant()).isEqualTo(TIME);
        }

        @Test
        @DisplayName("注入される時計を、テストから進められる")
        void advances() {
            // 準備：テストの時計を決めた時刻にする
            testClock.setInstant(TIME);

            // 操作：1 日進める
            testClock.advance(Duration.ofDays(1));

            // 確認：注入される時計が 1 日後になる
            assertThat(clock.instant()).isEqualTo(TIME.plus(Duration.ofDays(1)));
        }
    }
}

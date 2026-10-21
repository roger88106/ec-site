package com.ecsite.config;

import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.TestClockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 現在時刻（{@link Clock}）の検証（T016、バックエンドのアーキテクチャ 第 5 節）
 */
class ClockConfigTest {

    @Nested
    @IntegrationTest
    @DisplayName("通常の構成")
    class Default {

        @Autowired
        Clock clock;

        @Test
        @DisplayName("UTC のシステム時計が注入される")
        void systemClockInUtc() {
            assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
            assertThat(clock.instant()).isCloseTo(Instant.now(), within(Duration.ofSeconds(5)));
        }
    }

    @Nested
    @IntegrationTest
    @Import(TestClockConfig.class)
    @DisplayName("固定の時計に差し替えた構成")
    class Fixed {

        @Autowired
        Clock clock;

        @Autowired
        MutableClock testClock;

        @Test
        @DisplayName("注入される時計を、テストから決めた時刻にでき、進められる")
        void replacedByTestClock() {
            var time = Instant.parse("2026-10-07T15:00:00Z");

            testClock.setInstant(time);
            assertThat(clock.instant()).isEqualTo(time);

            testClock.advance(Duration.ofDays(1));
            assertThat(clock.instant()).isEqualTo(time.plus(Duration.ofDays(1)));
        }
    }
}

package com.ecsite;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;

/**
 * アプリの時計を、テストから時刻を決められる時計に差し替える。<br />
 * 時刻に依存するテストで {@code @Import(TestClockConfig.class)} し、{@link MutableClock} を注入して時刻を決める。
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfig {

    /**
     * テスト用の時計。アプリの {@link java.time.Clock} より優先して注入される。
     * 初期値は、テストの例で使う日付（2026-10-08 00:00 日本時間）。
     *
     * @return 時計
     */
    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(Instant.parse("2026-10-07T15:00:00Z"));
    }
}

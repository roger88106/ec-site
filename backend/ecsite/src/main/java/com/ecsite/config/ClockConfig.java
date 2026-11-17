package com.ecsite.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 現在時刻の設定
 *
 * <p>現在時刻を取る {@link Clock} の Bean を定義する。
 */
@Configuration
class ClockConfig {

    /**
     * UTC のシステム時計を作る。
     *
     * <p>時刻は UTC で扱う。
     *
     * @return 時計
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}

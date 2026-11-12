package com.ecsite.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 現在時刻の設定
 *
 * <p>現在時刻は必ずこの {@link Clock} から取る（{@code Instant.now()} などを直接呼ばない）。
 * ドメインには、ここから得た {@code Instant} を引数で渡す。
 */
@Configuration
class ClockConfig {

    /**
     * UTC のシステム時計。時刻は UTC で扱う。
     *
     * @return 時計
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}

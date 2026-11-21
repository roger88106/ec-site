package com.ecsite.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

/**
 * 乱数の設定
 *
 * <p>乱数を取る {@link RandomGenerator} の Bean を定義する。
 */
@Configuration
class RandomConfig {

    /**
     * 暗号論的に安全な乱数を作る。
     *
     * @return 乱数
     */
    @Bean
    RandomGenerator randomGenerator() {
        return new SecureRandom();
    }
}

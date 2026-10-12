package com.ecsite.config.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 認証と認可の設定（plan 003 第 3.1〜3.3 節）。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecurityProperties.class)
class SecurityConfig {

    /**
     * realm の署名の鍵。issuer の OpenID Connect の設定から公開鍵の URL を求める。
     *
     * @return realm の署名の鍵
     */
    @Bean
    RealmKeys realmKeys() {
        return issuerUri -> NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
    }

    /**
     * 認証を入れる前と同じく、すべての要求を通す（T3205 で、パスごとの認証と認可に置き換える）。
     *
     * @param http HTTP のセキュリティの設定
     * @return filter chain
     * @throws Exception 設定の誤り
     */
    @Bean
    SecurityFilterChain permitAllFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .build();
    }
}

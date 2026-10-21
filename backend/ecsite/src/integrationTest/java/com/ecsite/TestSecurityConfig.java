package com.ecsite;

import com.ecsite.config.security.RealmKeys;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 統合テストの認証の設定。IdP を起動せず、realm の署名の鍵を {@link TestTokens} の鍵に差し替える。
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestSecurityConfig {

    /**
     * 管理 API のパス
     */
    private static final String ADMIN_API = "/api/admin/";

    /**
     * 店舗 API のパス
     */
    private static final String STORE_API = "/api/store/";

    /**
     * テストの鍵で署名を確認する。
     *
     * @return realm の署名の鍵
     */
    @Bean
    @Primary
    RealmKeys testRealmKeys() {
        return issuerUri -> NimbusJwtDecoder.withPublicKey(TestTokens.publicKey(issuerUri)).build();
    }

    /**
     * テストだけの Controller（{@code /api/test-*}。エラーの応答の形などを確認する）を、認証なしで使えるようにする。<br />
     * 本番の filter chain は、決めていない URL を拒むため（バックエンドのアーキテクチャ 第 7.2 節）。
     *
     * @param http HTTP の設定
     * @return テストだけの Controller の filter chain
     */
    @Bean
    @Order(0)
    SecurityFilterChain testControllerFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/test-*/**")
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .build();
    }

    /**
     * MockMvc の要求に、既定の token を付ける。管理 API には管理者（すべての権限）の token（T3206）、
     * 店舗 API には既定の会員（{@link TestTokens#MEMBER_SUBJECT}）の token（T3312）。<br />
     * 認証と認可・持ち主を確認するテスト以外は、ログインを前提に業務の振る舞いを確認するので、要求ごとに token を書かない。
     * 店舗 API の会員を持たないカートは、token があっても今までどおり使える（AC-120）。
     * 要求に {@code Authorization} があれば付けない。要求の {@code with(...)} で
     * {@link TestTokens.Builder#bearer()} を付ければ置き換わり、{@link TestTokens#none()} を付ければ token なしになる
     * （既定の加工は、要求ごとの加工より先に行われる）。token は要求ごとに作る（有効期間が切れないように）。
     *
     * @return MockMvc の設定
     */
    @Bean
    MockMvcBuilderCustomizer tokenByDefault() {
        return builder -> builder.defaultRequest(get("/").with(request -> {
            if (request.getHeader(HttpHeaders.AUTHORIZATION) != null) {
                return request;
            }
            if (request.getRequestURI().startsWith(ADMIN_API)) {
                request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.administrator().encode());
            } else if (request.getRequestURI().startsWith(STORE_API)) {
                request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.member().encode());
            }
            return request;
        }));
    }
}

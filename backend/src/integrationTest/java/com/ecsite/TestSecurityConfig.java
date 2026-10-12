package com.ecsite;

import com.ecsite.config.security.RealmKeys;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

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
     * テストの鍵で署名を確かめる。
     *
     * @return realm の署名の鍵
     */
    @Bean
    @Primary
    RealmKeys testRealmKeys() {
        return issuerUri -> NimbusJwtDecoder.withPublicKey(TestTokens.publicKey(issuerUri)).build();
    }

    /**
     * MockMvc の管理 API の要求に、既定で管理者（すべての権限）の token を付ける（T3206）。<br />
     * 認証と認可を確かめるテスト以外は、権限を前提に業務の振る舞いを確かめるので、要求ごとに token を書かない。
     * 要求に {@code Authorization} があれば付けない。要求の {@code with(...)} で
     * {@link TestTokens.Builder#bearer()} を付ければ置き換わり、{@link TestTokens#none()} を付ければ token なしになる
     * （既定の加工は、要求ごとの加工より先に行われる）。token は要求ごとに作る（有効期間が切れないように）。
     *
     * @return MockMvc の設定
     */
    @Bean
    MockMvcBuilderCustomizer administratorTokenByDefault() {
        return builder -> builder.defaultRequest(get("/").with(request -> {
            if (request.getRequestURI().startsWith(ADMIN_API) && request.getHeader(HttpHeaders.AUTHORIZATION) == null) {
                request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.administrator().encode());
            }
            return request;
        }));
    }
}

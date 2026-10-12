package com.ecsite;

import com.ecsite.config.security.RealmKeys;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * 統合テストの認証の設定。IdP を起動せず、realm の署名の鍵を {@link TestTokens} の鍵に差し替える。
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestSecurityConfig {

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
}

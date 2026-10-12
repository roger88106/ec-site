package com.ecsite.config.security;

import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * realm の署名の鍵で token の署名を確かめる decoder を作る。<br />
 * 本番では、issuer の OpenID Connect の設定から公開鍵を取る（IdP から取ってキャッシュする）。
 * テストでは、IdP を起動せずに、テストの鍵に差し替える。
 * issuer・audience・有効期限の検証は、作った decoder に {@link SecurityConfig} が足す。
 */
@FunctionalInterface
public interface RealmKeys {

    /**
     * realm の decoder を作る。
     *
     * @param issuerUri realm の issuer
     * @return 署名を確かめる decoder
     */
    NimbusJwtDecoder decoder(String issuerUri);
}

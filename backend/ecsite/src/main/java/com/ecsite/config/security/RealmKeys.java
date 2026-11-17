package com.ecsite.config.security;

import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * realm の署名の鍵
 *
 * <p>realm の署名の鍵で token の署名を確認する decoder を作る。鍵の取り方を差し替えられる。
 */
@FunctionalInterface
public interface RealmKeys {

    /**
     * realm の decoder を作る。
     *
     * @param issuerUri realm の issuer
     * @return 署名を確認する decoder
     */
    NimbusJwtDecoder decoder(String issuerUri);
}

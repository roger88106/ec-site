package com.ecsite.config.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 認証の設定。realm ごとに、受け付ける access token の issuer と audience を持つ。<br />
 * 環境変数で上書きする（例：ECSITE_SECURITY_ADMIN_ISSUER_URI）。
 *
 * @param admin 運営者の realm（{@code /api/admin/**}）
 * @param store 消費者の realm（{@code /api/store/**}）
 */
@Validated
@ConfigurationProperties("ecsite.security")
public record SecurityProperties(
        @NotNull @Valid Realm admin,
        @NotNull @Valid Realm store) {

    /**
     * realm の設定
     *
     * @param issuerUri issuer（token の {@code iss}）。署名の鍵は、この URL の OpenID Connect の設定から取る
     * @param audience  audience（token の {@code aud} に含まれるべき値。API の client ID）
     */
    public record Realm(
            @NotBlank String issuerUri,
            @NotBlank String audience) {
    }
}

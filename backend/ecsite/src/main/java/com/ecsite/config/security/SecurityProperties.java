package com.ecsite.config.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 認証の設定
 *
 * <p>realm ごとに、受け付ける access token の issuer と audience を持つ。環境変数で上書きする（例：ECSITE_SECURITY_ADMIN_ISSUER_URI）。
 *
 * @param admin                         運営者の realm（{@code /api/admin/**}）
 * @param store                         消費者の realm（{@code /api/store/**}）
 * @param allowManagementOnBusinessPort 運用のパス（死活監視とメトリクス）を業務ポートで公開することを許すか。
 *                                      許さなければ、運用ポートが業務ポートと同じ設定で起動に失敗する
 */
@Validated
@ConfigurationProperties("ecsite.security")
public record SecurityProperties(
        @NotNull @Valid Realm admin,
        @NotNull @Valid Realm store,
        @DefaultValue("false") boolean allowManagementOnBusinessPort) {

    /**
     * 運営者と消費者の realm の issuer が違うかを返す。
     *
     * <p>値の検証で検査し、同じなら起動に失敗させる（会員の token で、管理 API の認証を通れるようになるため）。
     * token の issuer は文字列の完全な一致で確認されるので、ここも完全な一致で比べる。
     *
     * @return 違うか、項目の検証で失敗するなら true
     */
    @AssertTrue(message = "admin.issuer-uri と store.issuer-uri は違う値にする")
    boolean isRealmIssuersDistinct() {
        // realm か issuer がないときは、項目の検証が失敗にする
        if (admin == null || store == null || admin.issuerUri() == null || store.issuerUri() == null) {
            return true;
        }
        return !admin.issuerUri().equals(store.issuerUri());
    }

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

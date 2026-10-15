package com.ecsite;

import com.ecsite.common.model.value.Operator;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * テスト用の access token（plan 003 第 3.1 節）。<br />
 * IdP（Keycloak）を起動せず、realm ごとのテストの鍵で署名した token を作る。バックエンドは、
 * {@link TestSecurityConfig} が差し替えた公開鍵で署名を確かめ、本番と同じ issuer・audience・有効期限・権限の処理を通す。
 * token の中身は Keycloak が発行するものに合わせる（権限は {@code resource_access.ec-admin-api.roles}）。
 * <pre>{@code
 * mockMvc.perform(get("/api/admin/products").with(TestTokens.operator("product:read").bearer()))
 * }</pre>
 */
public final class TestTokens {

    /**
     * 運営者の realm の issuer（application-integration-test.yml と同じ）
     */
    public static final String ADMIN_ISSUER = "http://idp.test/realms/ec-admin";

    /**
     * 消費者の realm の issuer（application-integration-test.yml と同じ）
     */
    public static final String STORE_ISSUER = "http://idp.test/realms/ec-store";

    /**
     * 管理 API の audience
     */
    public static final String ADMIN_AUDIENCE = "ec-admin-api";

    /**
     * 店舗 API の audience
     */
    public static final String STORE_AUDIENCE = "ec-store-api";

    /**
     * 既定の運営者の IdP のアカウントの ID
     */
    public static final UUID OPERATOR_SUBJECT = UUID.fromString("00000000-0000-0000-0000-0000000a0001");

    /**
     * {@link #operator} の token の運営者（氏名は「姓 名」）。テストのデータを運営者の操作として作るときに使う
     */
    public static final Operator OPERATOR = new Operator(OPERATOR_SUBJECT, "管理 花子");

    /**
     * 既定の会員の IdP のアカウントの ID
     */
    public static final UUID MEMBER_SUBJECT = UUID.fromString("00000000-0000-0000-0000-0000000c0001");

    /**
     * すべての権限（運営者の realm の {@code ec-admin-api} の client role。職務の「管理者」と同じ）
     */
    public static final List<String> ALL_PERMISSIONS = List.of(
            "tax:read", "tax:write",
            "category:read", "category:write",
            "product:read", "product:write",
            "inventory:read", "inventory:write",
            "order:read", "order:write",
            "report-sales-trend:read", "report-variant-sales:read", "report-customer:read");

    /**
     * 参照の権限（運営者の realm の職務の「閲覧者」と同じ。レポートは含まない）
     */
    public static final List<String> READ_PERMISSIONS = List.of(
            "tax:read", "category:read", "product:read", "inventory:read", "order:read");

    /**
     * 運営者の realm の署名の鍵
     */
    private static final RSAKey ADMIN_KEY = generateKey();

    /**
     * 消費者の realm の署名の鍵
     */
    private static final RSAKey STORE_KEY = generateKey();

    /**
     * どの realm のものでもない鍵（署名の不正を確かめるため）
     */
    private static final RSAKey UNKNOWN_KEY = generateKey();

    private TestTokens() {
    }

    /**
     * 参照の権限に、権限を足す。
     */
    private static String[] withReadPermissions(String... permissions) {
        var all = new ArrayList<>(READ_PERMISSIONS);
        all.addAll(List.of(permissions));
        return all.toArray(String[]::new);
    }

    /**
     * 運営者の token（運営者の realm）。
     *
     * @param permissions 権限（{@code resource:action}）
     * @return token の組み立て
     */
    public static Builder operator(String... permissions) {
        return new Builder(ADMIN_ISSUER, ADMIN_AUDIENCE, ADMIN_KEY, OPERATOR_SUBJECT, "管理", "花子")
                .email("admin@example.com")
                .permissions(permissions);
    }

    /**
     * すべての権限を持つ運営者（職務の「管理者」）の token。
     *
     * @return token の組み立て
     */
    public static Builder administrator() {
        return operator(ALL_PERMISSIONS.toArray(String[]::new));
    }

    /**
     * 職務の「閲覧者」の token（参照の権限だけ）。
     *
     * @return token の組み立て
     */
    public static Builder viewer() {
        return operator(READ_PERMISSIONS.toArray(String[]::new));
    }

    /**
     * 職務の「管理職」の token（参照の権限と、すべてのレポート。spec 010 BR-28）。
     *
     * @return token の組み立て
     */
    public static Builder manager() {
        return operator(withReadPermissions("report-sales-trend:read", "report-variant-sales:read", "report-customer:read"));
    }

    /**
     * 職務の「営業」の token（参照の権限と、売上の推移を除くレポート。spec 010 BR-28）。
     *
     * @return token の組み立て
     */
    public static Builder sales() {
        return operator(withReadPermissions("report-variant-sales:read", "report-customer:read"));
    }

    /**
     * 会員の token（消費者の realm）。権限は持たない。
     *
     * @return token の組み立て
     */
    public static Builder member() {
        return new Builder(STORE_ISSUER, STORE_AUDIENCE, STORE_KEY, MEMBER_SUBJECT, "会員", "太郎")
                .email("member@example.com");
    }

    /**
     * 要求から {@code Authorization} のヘッダーを除く（認証のない要求）。
     *
     * @return 要求の加工
     */
    public static RequestPostProcessor none() {
        return request -> {
            request.removeHeader(HttpHeaders.AUTHORIZATION);
            return request;
        };
    }

    /**
     * realm の公開鍵（{@link TestSecurityConfig} が、バックエンドの署名の検証に使う）。
     *
     * @param issuer realm の issuer
     * @return 公開鍵
     */
    static RSAPublicKey publicKey(String issuer) {
        try {
            return switch (issuer) {
                case ADMIN_ISSUER -> ADMIN_KEY.toRSAPublicKey();
                case STORE_ISSUER -> STORE_KEY.toRSAPublicKey();
                default -> throw new IllegalArgumentException("テストの鍵がない issuer：" + issuer);
            };
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * token の組み立て。既定は、有効な token（発行は今、有効期間 5 分。plan 003 第 2.3 節）。
     */
    public static final class Builder {

        private String issuer;
        private String audience;
        private RSAKey key;
        private UUID subject;
        private String familyName;
        private String givenName;
        private String email;
        private boolean emailVerified = true;
        private String birthdate;
        private String gender;
        private List<String> permissions = List.of();
        private Instant expiresAt = Instant.now().plus(Duration.ofMinutes(5));

        private Builder(String issuer, String audience, RSAKey key, UUID subject, String familyName, String givenName) {
            this.issuer = issuer;
            this.audience = audience;
            this.key = key;
            this.subject = subject;
            this.familyName = familyName;
            this.givenName = givenName;
        }

        /**
         * @param subject IdP のアカウントの ID
         * @return this
         */
        public Builder subject(UUID subject) {
            this.subject = subject;
            return this;
        }

        /**
         * @param familyName 姓
         * @param givenName  名
         * @return this
         */
        public Builder name(String familyName, String givenName) {
            this.familyName = familyName;
            this.givenName = givenName;
            return this;
        }

        /**
         * @param email メールアドレス
         * @return this
         */
        public Builder email(String email) {
            this.email = email;
            return this;
        }

        /**
         * @param emailVerified メールアドレスが確認済みか
         * @return this
         */
        public Builder emailVerified(boolean emailVerified) {
            this.emailVerified = emailVerified;
            return this;
        }

        /**
         * @param birthdate 生年月日（会員の任意の属性。null なら載せない）
         * @return this
         */
        public Builder birthdate(String birthdate) {
            this.birthdate = birthdate;
            return this;
        }

        /**
         * @param gender 性別（会員の任意の属性。null なら載せない）
         * @return this
         */
        public Builder gender(String gender) {
            this.gender = gender;
            return this;
        }

        /**
         * @param permissions 権限（{@code resource:action}）
         * @return this
         */
        public Builder permissions(String... permissions) {
            this.permissions = List.of(permissions);
            return this;
        }

        /**
         * @param issuer issuer（別の realm・知らない IdP の token を作るため）
         * @return this
         */
        public Builder issuer(String issuer) {
            this.issuer = issuer;
            return this;
        }

        /**
         * @param audience audience
         * @return this
         */
        public Builder audience(String audience) {
            this.audience = audience;
            return this;
        }

        /**
         * @param expiresAt 有効期限
         * @return this
         */
        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        /**
         * どの realm のものでもない鍵で署名する（署名の不正）。
         *
         * @return this
         */
        public Builder signedByUnknownKey() {
            this.key = UNKNOWN_KEY;
            return this;
        }

        /**
         * 署名した token を作る。
         *
         * @return token（JWS のコンパクトな形）
         */
        public String encode() {
            var issuedAt = expiresAt.minus(Duration.ofMinutes(5));
            var claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .audience(audience)
                    .subject(subject.toString())
                    .issueTime(Date.from(issuedAt))
                    .expirationTime(Date.from(expiresAt))
                    .claim("typ", "Bearer")
                    .claim("email", email)
                    .claim("email_verified", emailVerified)
                    .claim("family_name", familyName)
                    .claim("given_name", givenName)
                    // Keycloak の name は「名 姓」の順
                    .claim("name", givenName + " " + familyName);
            // 会員の任意の属性は、入れていなければ Keycloak は claim を載せない
            if (birthdate != null) {
                claims.claim("birthdate", birthdate);
            }
            if (gender != null) {
                claims.claim("gender", gender);
            }
            if (!permissions.isEmpty()) {
                claims.claim("resource_access", Map.of(ADMIN_AUDIENCE, Map.of("roles", permissions)));
            }
            try {
                var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims.build());
                jwt.sign(new RSASSASigner(key));
                return jwt.serialize();
            } catch (JOSEException e) {
                throw new IllegalStateException(e);
            }
        }

        /**
         * 要求に {@code Authorization: Bearer} で token を付ける（すでにあれば置き換える）。
         *
         * @return 要求の加工
         */
        public RequestPostProcessor bearer() {
            var token = encode();
            return request -> {
                request.removeHeader(HttpHeaders.AUTHORIZATION);
                request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                return request;
            };
        }
    }
}

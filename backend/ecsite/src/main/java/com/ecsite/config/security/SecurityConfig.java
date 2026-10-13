package com.ecsite.config.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.util.Collection;
import java.util.List;

/**
 * 認証と認可の設定（第 3.1〜3.3 節、バックエンドのアーキテクチャ 第 7.2 節）。<br />
 * パスのプレフィックスごとに filter chain を分け、それぞれの realm の token だけを受け付ける。
 * <ul>
 *     <li>{@code /api/admin/**}：運営者の realm の token が必須。権限は {@code admin.yaml} で決める</li>
 *     <li>{@code /api/store/**}：消費者の realm の token。認証の要らない endpoint は今までどおり使える</li>
 *     <li>そのほか（{@code /actuator/**} など）：今までどおり</li>
 * </ul>
 * token はステートレスに検証する。Cookie を使わない（token をヘッダーで受ける）ので、CSRF の対策は無効にする。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecurityProperties.class)
class SecurityConfig {

    /**
     * realm の署名の鍵。issuer の OpenID Connect の設定から公開鍵の URL を求める（最初の検証のときに IdP から取る）。
     *
     * @return realm の署名の鍵
     */
    @Bean
    RealmKeys realmKeys() {
        return issuerUri -> NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
    }

    /**
     * 管理 API の操作ごとに必要な権限。ビルドのときにリソースに写した {@code admin.yaml} を、起動時に読む。
     *
     * @param adminApi {@code admin.yaml}
     * @return 操作ごとに必要な権限
     */
    @Bean
    AdminOperationPermissions adminOperationPermissions(@Value("classpath:openapi/admin.yaml") Resource adminApi) {
        return AdminOperationPermissions.load(adminApi);
    }

    /**
     * 認証・認可の失敗を Problem Details で返す。
     *
     * @param resolver Spring MVC の例外の解決
     * @return 401・403 の応答
     */
    @Bean
    SecurityProblemResponder securityProblemResponder(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        return new SecurityProblemResponder(resolver);
    }

    /**
     * 管理 API：運営者の realm の token と、{@code admin.yaml} の権限が必須。
     */
    @Bean
    @Order(1)
    SecurityFilterChain adminFilterChain(HttpSecurity http, SecurityProperties properties, RealmKeys keys,
                                         AdminOperationPermissions permissions, SecurityProblemResponder responder) throws Exception {
        var authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(new PermissionConverter(properties.admin().audience()));
        return stateless(http, responder)
                .securityMatcher("/api/admin/**")
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt
                                .decoder(decoder(properties.admin(), keys))
                                .jwtAuthenticationConverter(authenticationConverter))
                        .authenticationEntryPoint(responder)
                        .accessDeniedHandler(responder))
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().access(new AdminPermissionAuthorizationManager(permissions)))
                .build();
    }

    /**
     * 店舗 API：消費者の realm の token を受け付ける。会員の token には権限を載せない。
     */
    @Bean
    @Order(2)
    SecurityFilterChain storeFilterChain(HttpSecurity http, SecurityProperties properties, RealmKeys keys,
                                         SecurityProblemResponder responder) throws Exception {
        var authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(jwt -> List.<GrantedAuthority>of());
        return stateless(http, responder)
                .securityMatcher("/api/store/**")
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt
                                .decoder(decoder(properties.store(), keys))
                                .jwtAuthenticationConverter(authenticationConverter))
                        .authenticationEntryPoint(responder)
                        .accessDeniedHandler(responder))
                .authorizeHttpRequests(authorize -> authorize
                        // 会員の操作。token がなければ 401
                        .requestMatchers("/api/store/me/**", "/api/store/checkouts/**", "/api/store/orders/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/store/carts/*/checkouts").authenticated()
                        // 認証が不要・任意の操作
                        .anyRequest().permitAll())
                .build();
    }

    /**
     * そのほか（運用の口など）：今までどおり。
     */
    @Bean
    @Order(3)
    SecurityFilterChain defaultFilterChain(HttpSecurity http, SecurityProblemResponder responder) throws Exception {
        return stateless(http, responder)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .build();
    }

    /**
     * filter chain の共通の設定：ステートレス、CSRF の対策なし、失敗は Problem Details。
     */
    private static HttpSecurity stateless(HttpSecurity http, SecurityProblemResponder responder) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(responder)
                        .accessDeniedHandler(responder));
    }

    /**
     * realm の token の decoder。署名に加え、issuer・audience・有効期限を確かめる。<br />
     * 鍵は最初の検証のときに取る（起動のときに IdP が動いていなくてもよい）。
     *
     * @param realm realm の設定
     * @param keys  realm の署名の鍵
     * @return decoder
     */
    private static JwtDecoder decoder(SecurityProperties.Realm realm, RealmKeys keys) {
        return new SupplierJwtDecoder(() -> {
            var decoder = keys.decoder(realm.issuerUri());
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(realm.issuerUri()),
                    new JwtClaimValidator<Collection<String>>(JwtClaimNames.AUD,
                            audience -> audience != null && audience.contains(realm.audience()))));
            return decoder;
        });
    }
}

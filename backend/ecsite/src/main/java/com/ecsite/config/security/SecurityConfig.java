package com.ecsite.config.security;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderInitializationException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

/**
 * 認証と認可の設定
 *
 * <p>パスのプレフィックスごとに filter chain を分け、それぞれの realm の token だけを受け付ける。
 * 契約にない操作と、決めていない URL は拒む（認証がなければ 401）。
 * token はステートレスに検証する。Cookie を使わない（token をヘッダーで受ける）ので、CSRF の対策は無効にする。
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    /**
     * 決済の通知（Stripe）を受ける Controller のパス
     */
    private static final String PAYMENT_WEBHOOK_PATH = "/api/webhooks/stripe";

    /**
     * 物流サービスの配送状態の通知を受ける Controller のパス
     */
    private static final String SHIPPING_WEBHOOK_PATH = "/api/webhooks/carrier";

    /**
     * IdP への接続を待つ時間の上限（IdP の設定と鍵の取得の 1 回ごと）
     */
    private static final Duration IDP_CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /**
     * IdP の応答を待つ時間の上限（IdP の設定と鍵の取得の 1 回ごと）
     *
     * <p>検証の間、リクエストは IdP の応答を待つ。応答は小さい文書なので、長く待たない。
     * 接続と合わせても、ほかのリクエストの鍵の取得を Nimbus が待つ上限（15 秒）より短くし、待つ側が先に失敗しないようにする。
     */
    private static final Duration IDP_READ_TIMEOUT = Duration.ofSeconds(5);

    /**
     * realm の署名の鍵を作る。
     *
     * <p>issuer の OpenID Connect の設定から公開鍵の URL を求める（最初の検証のときに IdP から取る）。
     *
     * @return realm の署名の鍵
     */
    @Bean
    RealmKeys realmKeys() {
        return realmKeys(IDP_CONNECT_TIMEOUT, IDP_READ_TIMEOUT);
    }

    /**
     * realm の署名の鍵を、IdP を待つ時間の上限を設定して作る。
     *
     * <p>IdP の設定と鍵の取得の 1 回ごとに、接続と応答を待つ時間に上限を付ける。
     *
     * @param connectTimeout IdP への接続を待つ時間の上限
     * @param readTimeout    IdP の応答を待つ時間の上限
     * @return realm の署名の鍵
     */
    static RealmKeys realmKeys(Duration connectTimeout, Duration readTimeout) {
        // Spring Security の既定と同じ request factory で、時間の上限だけを変える
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        var rest = new RestTemplate(requestFactory);
        return issuerUri -> NimbusJwtDecoder.withIssuerLocation(issuerUri).restOperations(rest).build();
    }

    /**
     * 管理 API の操作ごとに必要な権限を読み込む。
     *
     * <p>ビルドのときにリソースに写した {@code admin.yaml} を、起動時に読む。
     *
     * @param adminApi {@code admin.yaml}
     * @return 操作ごとに必要な権限
     */
    @Bean
    AdminOperationPermissions adminOperationPermissions(@Value("classpath:openapi/admin.yaml") Resource adminApi) {
        return AdminOperationPermissions.load(adminApi);
    }

    /**
     * 店舗 API の操作ごとの token の要否を読み込む。
     *
     * <p>ビルドのときにリソースに写した {@code store.yaml} を、起動時に読む。
     *
     * @param storeApi {@code store.yaml}
     * @return 操作ごとの token の要否
     */
    @Bean
    StoreOperationSecurity storeOperationSecurity(@Value("classpath:openapi/store.yaml") Resource storeApi) {
        return StoreOperationSecurity.load(storeApi);
    }

    /**
     * 認証・認可の失敗を Problem Details で返す。
     *
     * @param resolver Spring MVC の例外の解決
     * @return 401・403・503 の Problem Details を返す応答
     */
    @Bean
    SecurityProblemResponder securityProblemResponder(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        return new SecurityProblemResponder(resolver);
    }

    /**
     * 運用ポートの設定を、起動のときに検査する。
     *
     * <p>Bean を遅延して作る設定（{@code spring.main.lazy-initialization}）でも、起動のときに作る。
     * 検査はどこにも注入されないので、遅延して作ると最後まで作られず、検査が動かないため。
     *
     * @param environment 設定
     * @param properties  認証の設定
     * @return 運用ポートの検査
     */
    @Bean
    @Lazy(false)
    ManagementPortCheck managementPortCheck(Environment environment, SecurityProperties properties) {
        return new ManagementPortCheck(environment, properties);
    }

    /**
     * mock のフラグを、起動のときに警告する。
     *
     * <p>Bean を遅延して作る設定（{@code spring.main.lazy-initialization}）でも、起動のときに作る。
     * 警告はどこにも注入されないので、遅延して作ると最後まで作られず、警告が出ないため。
     *
     * @param environment 設定
     * @return mock のフラグの警告
     */
    @Bean
    @Lazy(false)
    MockFlagWarning mockFlagWarning(Environment environment) {
        return new MockFlagWarning(environment);
    }

    /**
     * 管理 API の filter chain を作る。
     *
     * <p>運営者の realm の token と、{@code admin.yaml} の権限が必須。
     *
     * @param http        HttpSecurity
     * @param properties  認証の設定
     * @param keys        realm の署名の鍵
     * @param permissions 操作ごとに必要な権限
     * @param responder   認証・認可の失敗の応答
     * @return filter chain
     */
    @Bean
    @Order(1)
    SecurityFilterChain adminFilterChain(HttpSecurity http, SecurityProperties properties, RealmKeys keys,
                                         AdminOperationPermissions permissions, SecurityProblemResponder responder) throws Exception {
        return resourceServerChain(http, responder, "/api/admin/**", properties.admin(), keys,
                new PermissionConverter(properties.admin().audience()), new AdminPermissionAuthorizationManager(permissions));
    }

    /**
     * 店舗 API の filter chain を作る。
     *
     * <p>消費者の realm の token を受け付ける。会員の token には権限を載せない。token の要否は {@code store.yaml} で決める。
     *
     * @param http       HttpSecurity
     * @param properties 認証の設定
     * @param keys       realm の署名の鍵
     * @param security   操作ごとの token の要否
     * @param responder  認証・認可の失敗の応答
     * @return filter chain
     */
    @Bean
    @Order(2)
    SecurityFilterChain storeFilterChain(HttpSecurity http, SecurityProperties properties, RealmKeys keys,
                                         StoreOperationSecurity security, SecurityProblemResponder responder) throws Exception {
        return resourceServerChain(http, responder, "/api/store/**", properties.store(), keys,
                jwt -> List.<GrantedAuthority>of(), new StoreTokenAuthorizationManager(security));
    }

    /**
     * 管理 API と店舗 API 以外のパスの filter chain を作る。
     *
     * <p>既定で拒み、決めたパスだけを許す。mock の画面のパスは、そのモジュールの mock のフラグを立てたときだけ許す。
     *
     * @param http        HttpSecurity
     * @param responder   認証・認可の失敗の応答
     * @param environment 設定
     * @return filter chain
     */
    @Bean
    @Order(3)
    SecurityFilterChain defaultFilterChain(HttpSecurity http, SecurityProblemResponder responder, Environment environment) throws Exception {
        var mockPagePaths = MockPage.enabled(environment).stream().map(MockPage::getPathPattern).toArray(String[]::new);
        return stateless(http, responder)
                .authorizeHttpRequests(authorize -> {
                    // エラーの画面への転送を許さないと、許したパスの 404 が 401 に変わる
                    authorize.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                            // 通知のパスは署名で守る。通知の Controller のパスだけを許し、通知のパスの下のほかのパスは拒む
                            .requestMatchers(PAYMENT_WEBHOOK_PATH, SHIPPING_WEBHOOK_PATH).permitAll()
                            // 運用のパスは運用ポートで公開する（業務ポートで公開される設定は、許していなければ起動に失敗する）
                            .requestMatchers("/actuator/**").permitAll();
                    // フラグを立てたモジュールがなければ、許す mock の画面のパスもない
                    if (mockPagePaths.length > 0) {
                        authorize.requestMatchers(mockPagePaths).permitAll();
                    }
                    authorize.anyRequest().denyAll();
                })
                .build();
    }

    /**
     * filter chain の共通の設定：ステートレス、CSRF の対策なし、失敗は Problem Details。
     */
    private static HttpSecurity stateless(HttpSecurity http, SecurityProblemResponder responder) {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(responder)
                        .accessDeniedHandler(responder));
    }

    /**
     * realm の token を受ける filter chain を作る。
     *
     * <p>{@link #stateless} の共通の設定に、realm の token の検証と認可を追加する。
     *
     * @param http                 HttpSecurity
     * @param responder            認証・認可の失敗の応答
     * @param pathPattern          この filter chain が扱うパス
     * @param realm                realm の設定
     * @param keys                 realm の署名の鍵
     * @param authoritiesConverter token から権限を取り出す変換
     * @param authorizationManager リクエストごとの認可
     * @return filter chain
     * @throws Exception 設定の失敗
     */
    private static SecurityFilterChain resourceServerChain(
            HttpSecurity http, SecurityProblemResponder responder, String pathPattern, SecurityProperties.Realm realm, RealmKeys keys,
            Converter<Jwt, Collection<GrantedAuthority>> authoritiesConverter,
            AuthorizationManager<RequestAuthorizationContext> authorizationManager) throws Exception {
        var authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return stateless(http, responder)
                .securityMatcher(pathPattern)
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt
                                .decoder(decoder(realm, keys))
                                .jwtAuthenticationConverter(authenticationConverter))
                        .authenticationEntryPoint(responder)
                        .accessDeniedHandler(responder)
                        // 既定の失敗の処理は、IdP に届かないときの失敗を投げ直して 500 にする。configurer に失敗の処理を設定するメソッドがないので、filter を作った後に差し替える
                        .withObjectPostProcessor(new ObjectPostProcessor<BearerTokenAuthenticationFilter>() {
                            @Override
                            public <O extends BearerTokenAuthenticationFilter> O postProcess(O filter) {
                                filter.setAuthenticationFailureHandler(responder);
                                return filter;
                            }
                        }))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().access(authorizationManager))
                .build();
    }

    /**
     * realm の token の decoder を作る。
     *
     * <p>署名に加え、issuer・audience・有効期限を確認する。鍵は最初の検証のときに取る。
     * IdP の設定を取得できず decoder を作れないときは、鍵を取得できないときと同じ検証の処理の失敗（{@link JwtException}）にする。
     *
     * @param realm realm の設定
     * @param keys  realm の署名の鍵
     * @return decoder
     */
    private static JwtDecoder decoder(SecurityProperties.Realm realm, RealmKeys keys) {
        // 起動のときに IdP が動いていなくてもよいように、鍵は最初の検証のときに取る。作れなければ、次のリクエストで再作成する
        var lazy = new SupplierJwtDecoder(() -> {
            var decoder = keys.decoder(realm.issuerUri());
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(realm.issuerUri()),
                    new JwtClaimValidator<Collection<String>>(JwtClaimNames.AUD,
                            audience -> audience != null && audience.contains(realm.audience()))));
            return decoder;
        });
        return token -> {
            try {
                return lazy.decode(token);
            } catch (JwtDecoderInitializationException e) {
                // 認証の処理は JwtException だけを認証の失敗に変える。ほかの例外は filter の外に出て 500 になる
                throw new JwtException("IdP の設定を取得できず、decoder を作れない", e);
            }
        };
    }
}

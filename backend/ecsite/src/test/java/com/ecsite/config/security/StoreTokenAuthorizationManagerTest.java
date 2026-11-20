package com.ecsite.config.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 店舗 API の認可の単体テスト
 *
 * <p>{@code store.yaml} の {@code security} の token の要否で決めることを確認する。
 */
class StoreTokenAuthorizationManagerTest {

    // ============================== 定数 ==============================

    /**
     * 会員の token が必要な操作と、必要ない操作の契約
     */
    private static final String SPEC = """
            servers:
              - url: /api/store
            paths:
              /me/cart:
                get:
                  security:
                    - memberToken: []
              /products:
                get:
                  security: []
            """;

    // ============================== テストの対象 ==============================

    /**
     * 店舗 API の認可
     */
    private final StoreTokenAuthorizationManager manager = new StoreTokenAuthorizationManager(
            StoreOperationSecurity.load(new ByteArrayResource(SPEC.getBytes(StandardCharsets.UTF_8))));

    // ============================== テスト ==============================

    @Test
    @DisplayName("DC-477-8 token が必要な操作は、ログインした会員を許す")
    void grantsMemberOperationForMember() {
        // 操作と確認：会員がカートを求めると、許す
        assertThat(isGranted(member(), request("GET", "/api/store/me/cart"))).isTrue();
    }

    @Test
    @DisplayName("DC-477-8 token が必要な操作は、認証がなければ拒む")
    void deniesMemberOperationForAnonymous() {
        // 操作と確認：認証なしでカートを求めると、拒む
        assertThat(isGranted(anonymous(), request("GET", "/api/store/me/cart"))).isFalse();
    }

    @Test
    @DisplayName("DC-477-8 token が必要ない操作は、認証がなくても許す")
    void grantsPublicOperationForAnonymous() {
        // 操作と確認：認証なしで商品の一覧を求めると、許す
        assertThat(isGranted(anonymous(), request("GET", "/api/store/products"))).isTrue();
    }

    @Test
    @DisplayName("DC-477-8 契約にない操作は、ログインした会員でも拒む")
    void deniesUndeclaredOperation() {
        // 操作と確認：会員が商品の一覧を DELETE すると、拒む
        assertThat(isGranted(member(), request("DELETE", "/api/store/products"))).isFalse();
    }

    @Test
    @DisplayName("DC-477-8 コンテキストパスの下のリクエストは、コンテキストパスを除いたパスで token の要否を求める")
    void usesPathWithoutContextPath() {
        // 準備：コンテキストパスの下の、商品の一覧のリクエスト
        var request = new MockHttpServletRequest("GET", "/shop/api/store/products");
        request.setContextPath("/shop");

        // 操作と確認：認証なしで、許す
        assertThat(isGranted(anonymous(), request)).isTrue();
    }

    // ============================== 準備の補助 ==============================

    /**
     * 認可を求め、許したかを返す。
     *
     * @param authentication 認証
     * @param request        リクエスト
     * @return 許したら true
     */
    private boolean isGranted(Authentication authentication, MockHttpServletRequest request) {
        return manager.authorize(() -> authentication, new RequestAuthorizationContext(request)).isGranted();
    }

    /**
     * コンテキストパスのないリクエストを作る。
     *
     * @param method HTTP メソッド
     * @param uri    パス
     * @return リクエスト
     */
    private static MockHttpServletRequest request(String method, String uri) {
        return new MockHttpServletRequest(method, uri);
    }

    /**
     * ログインした会員の認証を作る（会員の token には権限を載せない）。
     *
     * @return 認証
     */
    private static Authentication member() {
        return new TestingAuthenticationToken("member", "token", AuthorityUtils.NO_AUTHORITIES);
    }

    /**
     * 認証のないリクエストの認証（匿名）を作る。
     *
     * @return 認証
     */
    private static Authentication anonymous() {
        return new AnonymousAuthenticationToken("key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
    }
}

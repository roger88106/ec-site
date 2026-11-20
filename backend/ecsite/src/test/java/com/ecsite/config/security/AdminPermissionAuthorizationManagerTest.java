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
 * 管理 API の認可の単体テスト
 *
 * <p>{@code admin.yaml} の {@code x-required-permission} の権限を、認証が持つかで決めることを確認する。
 */
class AdminPermissionAuthorizationManagerTest {

    // ============================== 定数 ==============================

    /**
     * 権限の必要な操作と、ログインしていればよい操作の契約
     */
    private static final String SPEC = """
            servers:
              - url: /api/admin
            paths:
              /tax-categories:
                get:
                  x-required-permission: tax:read
              /me:
                get:
                  x-required-permission: authenticated
            """;

    // ============================== テストの対象 ==============================

    /**
     * 管理 API の認可
     */
    private final AdminPermissionAuthorizationManager manager = new AdminPermissionAuthorizationManager(
            AdminOperationPermissions.load(new ByteArrayResource(SPEC.getBytes(StandardCharsets.UTF_8))));

    // ============================== テスト ==============================

    @Test
    @DisplayName("DC-477-8 操作の権限を持つ運営者は許す")
    void grantsWithRequiredPermission() {
        // 操作と確認：税区分の参照の権限で一覧を求めると、許す
        assertThat(isGranted(operator("tax:read"), request("GET", "/api/admin/tax-categories"))).isTrue();
    }

    @Test
    @DisplayName("DC-477-8 ほかの権限だけを持つ運営者は拒む")
    void deniesWithOtherPermission() {
        // 操作と確認：税区分の更新の権限だけで一覧を求めると、拒む
        assertThat(isGranted(operator("tax:write"), request("GET", "/api/admin/tax-categories"))).isFalse();
    }

    @Test
    @DisplayName("DC-477-8 ログインしていればよい操作は、権限のない運営者も許す")
    void grantsAuthenticatedOperationWithoutPermission() {
        // 操作と確認：権限のない運営者が自分の情報を求めると、許す
        assertThat(isGranted(operator(), request("GET", "/api/admin/me"))).isTrue();
    }

    @Test
    @DisplayName("DC-477-8 ログインしていればよい操作も、認証がなければ拒む")
    void deniesAuthenticatedOperationForAnonymous() {
        // 操作と確認：認証なしで自分の情報を求めると、拒む
        assertThat(isGranted(anonymous(), request("GET", "/api/admin/me"))).isFalse();
    }

    @Test
    @DisplayName("DC-477-8 契約にないメソッドは、権限を持っていても拒む")
    void deniesUndeclaredMethod() {
        // 操作と確認：税区分の参照と更新の権限で一覧を DELETE すると、拒む
        assertThat(isGranted(operator("tax:read", "tax:write"), request("DELETE", "/api/admin/tax-categories"))).isFalse();
    }

    @Test
    @DisplayName("DC-477-8 契約にないパスは、ログインしていても拒む")
    void deniesUndeclaredPath() {
        // 操作と確認：契約にないパスを求めると、拒む
        assertThat(isGranted(operator("tax:read"), request("GET", "/api/admin/undeclared"))).isFalse();
    }

    @Test
    @DisplayName("DC-477-8 コンテキストパスの下のリクエストは、コンテキストパスを除いたパスで権限を求める")
    void usesPathWithoutContextPath() {
        // 準備：コンテキストパスの下の、税区分の一覧のリクエスト
        var request = new MockHttpServletRequest("GET", "/shop/api/admin/tax-categories");
        request.setContextPath("/shop");

        // 操作と確認：税区分の参照の権限で、許す
        assertThat(isGranted(operator("tax:read"), request)).isTrue();
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
     * 権限を持つ、ログインした運営者の認証を作る。
     *
     * @param permissions 権限
     * @return 認証
     */
    private static Authentication operator(String... permissions) {
        return new TestingAuthenticationToken("operator", "token", permissions);
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

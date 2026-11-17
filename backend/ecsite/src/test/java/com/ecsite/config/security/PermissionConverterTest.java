package com.ecsite.config.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * token から権限への変換の単体テスト
 *
 * <p>権限は {@code resource_access.ec-admin-api.roles} だけから読む。
 */
class PermissionConverterTest {

    // ============================== テストの対象 ==============================

    /**
     * テストの対象（管理 API の client は {@code ec-admin-api}）
     */
    private final PermissionConverter converter = new PermissionConverter("ec-admin-api");

    // ============================== テスト ==============================

    @Test
    @DisplayName("管理 API の client role を、そのまま権限にする")
    void convertsAdminApiClientRoles() {
        // 準備：管理 API の client role を 2 つ持つ token
        var jwt = jwt(Map.of("resource_access", Map.of(
                "ec-admin-api", Map.of("roles", List.of("tax:read", "product:write")))));

        // 操作と確認：2 つの role が、そのまま権限になる
        assertThat(permissions(jwt)).containsExactlyInAnyOrder("tax:read", "product:write");
    }

    @Test
    @DisplayName("ほかの client の role と、realm の role は権限にしない")
    void ignoresOtherClientsAndRealmRoles() {
        // 準備：管理 API の role 1 つと、ほかの client・realm・scope に権限の形の値を持つ token
        var jwt = jwt(Map.of(
                "resource_access", Map.of(
                        "ec-admin-api", Map.of("roles", List.of("order:read")),
                        "account", Map.of("roles", List.of("manage-account", "tax:write")),
                        "ec-admin-web", Map.of("roles", List.of("product:write"))),
                "realm_access", Map.of("roles", List.of("administrator", "inventory:write")),
                "scope", "openid email profile"));

        // 操作と確認：管理 API の role だけが権限になる
        assertThat(permissions(jwt)).containsExactly("order:read");
    }

    /**
     * 管理 API の client role を持たない token の claim を返す。
     *
     * @return 場合の名前と claim
     */
    static Stream<Arguments> withoutAdminApiRoles() {
        return Stream.of(
                Arguments.of("resource_access がない", Map.of("scope", "openid")),
                Arguments.of("管理 API の client がない", Map.of("resource_access", Map.of("account", Map.of("roles", List.of("tax:read"))))),
                Arguments.of("管理 API の client に roles がない", Map.of("resource_access", Map.of("ec-admin-api", Map.of()))));
    }

    @ParameterizedTest(name = "「{0}」")
    @MethodSource("withoutAdminApiRoles")
    @DisplayName("管理 API の client role がなければ、権限はない")
    void noPermissionsWithoutAdminApiRoles(String name, Map<String, Object> claims) {
        // 操作と確認：権限が空
        assertThat(permissions(jwt(claims))).isEmpty();
    }

    /**
     * 形の正しくない claim を返す。
     *
     * @return 場合の名前と claim
     */
    static Stream<Arguments> malformedClaims() {
        return Stream.of(
                Arguments.of("resource_access が文字列", Map.of("resource_access", "tax:read")),
                Arguments.of("管理 API の client が文字列", Map.of("resource_access", Map.of("ec-admin-api", "tax:read"))),
                Arguments.of("roles が文字列", Map.of("resource_access", Map.of("ec-admin-api", Map.of("roles", "tax:read")))));
    }

    @ParameterizedTest(name = "「{0}」")
    @MethodSource("malformedClaims")
    @DisplayName("形の正しくない claim は、権限にしない")
    void ignoresMalformedClaims(String name, Map<String, Object> claims) {
        // 操作と確認：権限が空
        assertThat(permissions(jwt(claims))).isEmpty();
    }

    @Test
    @DisplayName("roles の中の文字列でない値は権限にせず、文字列の値だけを権限にする")
    void ignoresNonStringRoles() {
        // 準備：roles に数と文字列を持つ token
        var jwt = jwt(Map.of("resource_access", Map.of("ec-admin-api", Map.of("roles", List.of(1, "tax:read")))));

        // 操作と確認：文字列の値だけが権限になる
        assertThat(permissions(jwt)).containsExactly("tax:read");
    }

    // ============================== 準備の補助 ==============================

    /**
     * token を権限に変換し、権限の名前を返す。
     *
     * @param jwt token
     * @return 権限の名前
     */
    private List<String> permissions(Jwt jwt) {
        return converter.convert(jwt).stream().map(GrantedAuthority::getAuthority).toList();
    }

    /**
     * claim を持つ token を作る。
     *
     * @param claims claim
     * @return token
     */
    private static Jwt jwt(Map<String, Object> claims) {
        var builder = Jwt.withTokenValue("token").header("alg", "RS256").subject("subject");
        claims.forEach(builder::claim);
        return builder.build();
    }
}

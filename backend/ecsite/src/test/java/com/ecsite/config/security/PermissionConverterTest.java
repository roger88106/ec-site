package com.ecsite.config.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * token から権限への変換の単体テスト。<br />
 * 権限は {@code resource_access.ec-admin-api.roles} だけから読む。
 */
class PermissionConverterTest {

    private final PermissionConverter converter = new PermissionConverter("ec-admin-api");

    @Test
    @DisplayName("管理 API の client role を、そのまま権限にする")
    void convertsAdminApiClientRoles() {
        var jwt = jwt(Map.of("resource_access", Map.of(
                "ec-admin-api", Map.of("roles", List.of("tax:read", "product:write")))));

        assertThat(permissions(jwt)).containsExactlyInAnyOrder("tax:read", "product:write");
    }

    @Test
    @DisplayName("ほかの client の role と、realm の role は権限にしない")
    void ignoresOtherClientsAndRealmRoles() {
        var jwt = jwt(Map.of(
                "resource_access", Map.of(
                        "ec-admin-api", Map.of("roles", List.of("order:read")),
                        "account", Map.of("roles", List.of("manage-account", "tax:write")),
                        "ec-admin-web", Map.of("roles", List.of("product:write"))),
                "realm_access", Map.of("roles", List.of("administrator", "inventory:write")),
                "scope", "openid email profile"));

        assertThat(permissions(jwt)).containsExactly("order:read");
    }

    @Test
    @DisplayName("管理 API の client role がなければ、権限はない")
    void noPermissionsWithoutAdminApiRoles() {
        assertThat(permissions(jwt(Map.of("scope", "openid")))).isEmpty();
        assertThat(permissions(jwt(Map.of("resource_access", Map.of("account", Map.of("roles", List.of("tax:read"))))))).isEmpty();
        assertThat(permissions(jwt(Map.of("resource_access", Map.of("ec-admin-api", Map.of()))))).isEmpty();
    }

    @Test
    @DisplayName("形の正しくない値は、権限にしない")
    void ignoresMalformedClaims() {
        assertThat(permissions(jwt(Map.of("resource_access", "tax:read")))).isEmpty();
        assertThat(permissions(jwt(Map.of("resource_access", Map.of("ec-admin-api", "tax:read"))))).isEmpty();
        assertThat(permissions(jwt(Map.of("resource_access", Map.of("ec-admin-api", Map.of("roles", "tax:read")))))).isEmpty();
        assertThat(permissions(jwt(Map.of("resource_access", Map.of("ec-admin-api", Map.of("roles", List.of(1, "tax:read")))))))
                .containsExactly("tax:read");
    }

    private List<String> permissions(Jwt jwt) {
        return converter.convert(jwt).stream().map(GrantedAuthority::getAuthority).toList();
    }

    private static Jwt jwt(Map<String, Object> claims) {
        var builder = Jwt.withTokenValue("token").header("alg", "RS256").subject("subject");
        claims.forEach(builder::claim);
        return builder.build();
    }
}

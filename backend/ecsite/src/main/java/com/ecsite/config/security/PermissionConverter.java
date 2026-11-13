package com.ecsite.config.security;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * token から権限への変換
 *
 * <p>権限は、運営者の realm が管理 API の client role として発行したもの（{@code resource_access.<管理 API>.roles}）だけ。
 * 名前（{@code resource:action}）をそのまま Spring Security の権限にする。
 * ほかの client の role と realm の role（職務の名前）は権限にしない。
 */
@RequiredArgsConstructor
class PermissionConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    /**
     * token の、client ごとの role の項目
     */
    private static final String RESOURCE_ACCESS = "resource_access";

    /**
     * client の role の一覧の項目
     */
    private static final String ROLES = "roles";

    /**
     * 権限の role を持つ client（管理 API の audience）
     */
    @NonNull
    private final String clientId;

    /**
     * 管理 API の client role を権限にする。
     *
     * <p>client role がなければ、権限なし。
     *
     * @param jwt token
     * @return 権限
     */
    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        // token の resource_access.<client>.roles をたどる。途中のどれかがなければ、権限なし
        if (!(jwt.getClaims().get(RESOURCE_ACCESS) instanceof Map<?, ?> resourceAccess)
                || !(resourceAccess.get(clientId) instanceof Map<?, ?> client)
                || !(client.get(ROLES) instanceof Collection<?> roles)) {
            return List.of();
        }
        // 文字の client role を、そのまま権限にする
        return roles.stream()
                .filter(String.class::isInstance)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority((String) role))
                .toList();
    }
}

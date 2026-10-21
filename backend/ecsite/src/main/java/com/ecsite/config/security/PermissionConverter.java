package com.ecsite.config.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * token から権限への変換（バックエンドのアーキテクチャ 第 7.2 節）。権限を読むのはここだけにする。<br />
 * 権限は、運営者の realm が管理 API の client role として発行したもの（{@code resource_access.<管理 API>.roles}）だけ。
 * 名前（{@code resource:action}）をそのまま Spring Security の権限にする。
 * ほかの client の role と realm の role（職務の名前）は権限にしない。権限の受け渡し方を変えるときは、ここだけを変える。
 */
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
    private final String clientId;

    /**
     * @param clientId 権限の role を持つ client ID
     */
    PermissionConverter(String clientId) {
        this.clientId = clientId;
    }

    /**
     * 管理 API の client role を権限にする。client role がなければ、権限なし。
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

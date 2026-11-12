package com.ecsite.common.security;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

/**
 * リクエストの token の取り出し
 *
 * <p>認証情報がない、匿名、token でない認証のときは、token がないとみなす。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CurrentToken {

    /**
     * リクエストの token を返す。
     *
     * @return token。token で認証していなければ空
     */
    public static Optional<Jwt> find() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token) {
            return Optional.of(token.getToken());
        }
        return Optional.empty();
    }

    /**
     * リクエストの token を返す。
     *
     * <p>なければ業務のエラーにする。
     *
     * @return token
     * @throws BusinessException token で認証していない（{@code UNAUTHENTICATED}）
     */
    public static Jwt require() {
        return find().orElseThrow(() -> BusinessException.of(CommonErrorCode.UNAUTHENTICATED));
    }
}

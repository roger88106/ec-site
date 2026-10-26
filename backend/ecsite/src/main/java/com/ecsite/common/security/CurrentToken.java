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
 * 要求の認証情報から token を取り出す。{@link CurrentMember}、{@link CurrentOperator}、自分の権限の一覧の API が使う。<br />
 * 認証情報がない、匿名、token でない認証のときは、token がないとみなす。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CurrentToken {

    /**
     * 要求の token を返す。
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
     * 要求の token を返す。認証を必須にした API で使う（filter で認証を必須にしているが、念のため確認する）。
     *
     * @return token
     * @throws BusinessException token で認証していない（401）
     */
    public static Jwt require() {
        return find().orElseThrow(() -> BusinessException.of(CommonErrorCode.UNAUTHENTICATED));
    }
}

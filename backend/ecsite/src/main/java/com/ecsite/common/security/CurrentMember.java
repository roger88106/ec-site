package com.ecsite.common.security;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 要求の token の会員を取り出す。店舗 API の presentation 層が使う。<br />
 * Spring Security の型は、ここより先（application 層）に出さない（constitution 10.2）。
 * 店舗 API の filter chain は消費者の realm の token だけを受け付けるので、ここで取り出せるのは会員だけ。
 * 管理 API の presentation 層では使わない。
 */
@Component
public class CurrentMember {

    /**
     * token の会員を返す。
     *
     * @return 会員。token がなければ（ログインしていなければ）空
     */
    public Optional<AuthenticatedMember> find() {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token)) {
            return Optional.empty();
        }
        var jwt = token.getToken();
        return Optional.of(new AuthenticatedMember(
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")),
                jwt.getClaimAsString("family_name"),
                jwt.getClaimAsString("given_name")));
    }

    /**
     * token の会員を返す。会員の操作で使う（filter で認証を必須にしているが、念のため確かめる）。
     *
     * @return 会員
     * @throws BusinessException token がない（401）
     */
    public AuthenticatedMember require() {
        return find().orElseThrow(() -> BusinessException.of(CommonErrorCode.UNAUTHENTICATED));
    }
}

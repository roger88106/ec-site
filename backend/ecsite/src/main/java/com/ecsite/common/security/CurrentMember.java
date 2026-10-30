package com.ecsite.common.security;

import com.ecsite.common.error.exception.BusinessException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 要求の token の会員を取り出す。店舗 API の presentation 層が使う。<br />
 * Spring Security の型は、ここより先（application 層）に出さない（バックエンドのアーキテクチャの「業務のコードとの境界」）。
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
        return CurrentToken.find().map(CurrentMember::toMember);
    }

    /**
     * token の会員を返す。会員の操作で使う（filter で認証を必須にしているが、念のため確認する）。
     *
     * @return 会員
     * @throws BusinessException token がない（401）
     */
    public AuthenticatedMember require() {
        return toMember(CurrentToken.require());
    }

    /**
     * token から会員を作る。
     *
     * @param jwt 会員の token
     * @return 会員
     */
    private static AuthenticatedMember toMember(Jwt jwt) {
        return new AuthenticatedMember(
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")),
                jwt.getClaimAsString("family_name"),
                jwt.getClaimAsString("given_name"),
                jwt.getClaimAsString("birthdate"),
                jwt.getClaimAsString("gender"));
    }
}

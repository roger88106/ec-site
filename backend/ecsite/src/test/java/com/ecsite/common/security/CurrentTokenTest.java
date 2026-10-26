package com.ecsite.common.security;

import com.ecsite.common.error.CommonErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 要求の token を取り出す処理の単体テスト
 */
class CurrentTokenTest {

    /**
     * ログインの状態を消す
     */
    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("token で認証していれば、find も require もその token を返す")
    void returnsToken() {
        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject("subject-1")
                .issuedAt(Instant.EPOCH).expiresAt(Instant.EPOCH.plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        assertThat(CurrentToken.find()).contains(jwt);
        assertThat(CurrentToken.require()).isSameAs(jwt);
    }

    @Test
    @DisplayName("認証の情報がなければ、find は空、require は 401 の業務のエラー")
    void handlesNoAuthentication() {
        assertThat(CurrentToken.find()).isEmpty();
        assertBusinessError(CurrentToken::require, CommonErrorCode.UNAUTHENTICATED);
    }

    @Test
    @DisplayName("匿名の認証なら、find は空、require は 401 の業務のエラー")
    void handlesAnonymous() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymous",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(CurrentToken.find()).isEmpty();
        assertBusinessError(CurrentToken::require, CommonErrorCode.UNAUTHENTICATED);
    }

    @Test
    @DisplayName("token でない認証なら、find は空、require は 401 の業務のエラー")
    void handlesOtherAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user", "password"));

        assertThat(CurrentToken.find()).isEmpty();
        assertBusinessError(CurrentToken::require, CommonErrorCode.UNAUTHENTICATED);
    }
}

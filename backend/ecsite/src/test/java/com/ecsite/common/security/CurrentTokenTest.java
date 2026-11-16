package com.ecsite.common.security;

import com.ecsite.common.error.CommonErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.stream.Stream;

import static com.ecsite.support.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * リクエストの token を取り出す処理の単体テスト
 */
class CurrentTokenTest {

    /**
     * ログインの状態を消す
     */
    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("token で認証しているとき")
    class Authenticated {

        @Test
        @DisplayName("find はその token を返す")
        void findReturnsToken() {
            // 準備：token で認証する
            var jwt = authenticateWithToken();

            // 操作と確認：その token
            assertThat(CurrentToken.find()).contains(jwt);
        }

        @Test
        @DisplayName("require はその token を返す")
        void requireReturnsToken() {
            // 準備：token で認証する
            var jwt = authenticateWithToken();

            // 操作と確認：同じ token
            assertThat(CurrentToken.require()).isSameAs(jwt);
        }
    }

    @Nested
    @DisplayName("token で認証していないとき")
    class NotAuthenticated {

        @ParameterizedTest(name = "「{0}」")
        @MethodSource("com.ecsite.common.security.CurrentTokenTest#authenticationsWithoutToken")
        @DisplayName("find は空")
        void findReturnsEmpty(String kind, Authentication authentication) {
            // 準備：token でない認証の状態にする
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 操作と確認：空
            assertThat(CurrentToken.find()).isEmpty();
        }

        @ParameterizedTest(name = "「{0}」")
        @MethodSource("com.ecsite.common.security.CurrentTokenTest#authenticationsWithoutToken")
        @DisplayName("require は 401 の業務のエラー")
        void requireRejects(String kind, Authentication authentication) {
            // 準備：token でない認証の状態にする
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 操作と確認：業務のエラー UNAUTHENTICATED
            assertBusinessError(CurrentToken::require, CommonErrorCode.UNAUTHENTICATED);
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * token でない認証の状態を返す。
     *
     * @return 状態の名前と、認証（認証の情報がないときは null）
     */
    static Stream<Arguments> authenticationsWithoutToken() {
        return Stream.of(
                Arguments.of("認証の情報がない", null),
                Arguments.of("匿名の認証", new AnonymousAuthenticationToken("key", "anonymous",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"))),
                Arguments.of("token でない認証", new TestingAuthenticationToken("user", "password")));
    }

    /**
     * token で認証した状態にする。
     *
     * @return 認証に使った token
     */
    private static Jwt authenticateWithToken() {
        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject("subject-1")
                .issuedAt(Instant.EPOCH).expiresAt(Instant.EPOCH.plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        return jwt;
    }
}

package com.ecsite.config.security;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BaseException;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.error.exception.SystemException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.RemoteKeySourceException;
import com.nimbusds.jose.jwk.source.JWKSetUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtDecoderInitializationException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 認証の失敗の応答の単体テスト
 *
 * <p>token の検証の失敗の原因ごとに、エラーハンドラーに渡す例外（エラーコード）と、{@code WWW-Authenticate} の有無を確認する。
 * 失敗は、Spring Security が token の検証の処理の失敗を包むのと同じ形（{@link AuthenticationServiceException} の中の {@link JwtException}）で作る。
 */
class SecurityProblemResponderTest {

    // ============================== テスト ==============================

    /**
     * IdP に届かない失敗の、ケース名と失敗を返す。
     *
     * @return テストの入力
     */
    static Stream<Arguments> idpUnreachableCases() {
        return Stream.of(
                Arguments.of("IdP の設定を取得できず、decoder を作れない",
                        serviceFailure(new JwtDecoderInitializationException("Failed to lazily resolve the supplied JwtDecoder instance",
                                new IllegalArgumentException("Unable to resolve the Configuration with the provided Issuer")))),
                Arguments.of("署名の鍵の取得に失敗した",
                        serviceFailure(new RemoteKeySourceException("Couldn't retrieve JWK set",
                                new ResourceAccessException("I/O error on GET request")))),
                Arguments.of("ほかのリクエストの署名の鍵の取得を待ちきれない",
                        serviceFailure(new JWKSetUnavailableException("Timeout while waiting for cache refresh"))));
    }

    @ParameterizedTest(name = "「{0}」")
    @MethodSource("idpUnreachableCases")
    @DisplayName("DC-477-11 IdP に届かず token を検証できないとき、IdP に届かないことを示すエラーにし、WWW-Authenticate を付けない")
    void respondsAuthenticationUnavailable(String name, AuthenticationException failure) {
        // 操作：token の検証の失敗を渡す
        var response = new MockHttpServletResponse();
        responder.onAuthenticationFailure(new MockHttpServletRequest(), response, failure);

        // 確認：IdP に届かないことを示すシステムのエラーで、原因に失敗を持つ
        assertThat(resolved).singleElement().isInstanceOfSatisfying(SystemException.class, error -> {
            assertErrorCode(error, CommonErrorCode.AUTHENTICATION_UNAVAILABLE);
            assertThat(error).hasCause(failure);
        });

        // 確認：token の誤りでないので、WWW-Authenticate を付けない
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();
    }

    /**
     * IdP に届いたうえでの検証の失敗の、ケース名と失敗を返す。
     *
     * @return テストの入力
     */
    static Stream<Arguments> unauthenticatedCases() {
        return Stream.of(
                Arguments.of("署名の確認の処理に失敗した（鍵は取得できた）", serviceFailure(new JOSEException("Unsupported JWS algorithm"))),
                Arguments.of("原因のない検証の処理の失敗", new AuthenticationServiceException("An error occurred while attempting to decode the Jwt")),
                Arguments.of("token が無効", new InvalidBearerTokenException("Invalid token")));
    }

    @ParameterizedTest(name = "「{0}」")
    @MethodSource("unauthenticatedCases")
    @DisplayName("DC-477-11 IdP に届かないこと以外の検証の失敗は、認証の失敗にし、WWW-Authenticate に Bearer を付ける")
    void respondsUnauthenticated(String name, AuthenticationException failure) {
        // 操作：token の検証の失敗を渡す
        var response = new MockHttpServletResponse();
        responder.onAuthenticationFailure(new MockHttpServletRequest(), response, failure);

        // 確認：認証の失敗を示す業務のエラー
        assertThat(resolved).singleElement().isInstanceOfSatisfying(BusinessException.class,
                error -> assertErrorCode(error, CommonErrorCode.UNAUTHENTICATED));

        // 確認：Bearer の token が必要なことを示す
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
    }

    // ============================== 確認の補助 ==============================

    /**
     * 例外のエラーコードが、期待するエラーコードであることを確認する。
     *
     * @param error     例外
     * @param errorCode 期待するエラーコード
     */
    private static void assertErrorCode(BaseException error, CommonErrorCode errorCode) {
        assertThat(error.getErrorDetail().errorCode().code()).isEqualTo(errorCode.getCode().code());
    }

    // ============================== 準備の補助 ==============================

    /**
     * エラーハンドラーに渡された例外（渡された順）
     */
    private final List<Exception> resolved = new ArrayList<>();

    /**
     * 渡された例外を記録するエラーハンドラーに渡す、認証の失敗の応答
     */
    private final SecurityProblemResponder responder = new SecurityProblemResponder((request, response, handler, exception) -> {
        resolved.add(exception);
        return new ModelAndView();
    });

    /**
     * token の検証の処理の失敗を、Spring Security と同じ形に包む。
     *
     * @param cause decoder の中の失敗
     * @return 検証の処理の失敗
     */
    private static AuthenticationServiceException serviceFailure(Exception cause) {
        return new AuthenticationServiceException("An error occurred while attempting to decode the Jwt",
                new JwtException("An error occurred while attempting to decode the Jwt", cause));
    }
}

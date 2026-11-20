package com.ecsite.config.security;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.error.exception.SystemException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 認証・認可の失敗の応答
 *
 * <p>ほかのエラーと同じ Problem Details で返す。filter で起きた失敗を、業務の例外・システムの例外（エラーコード {@code UNAUTHENTICATED}、{@code FORBIDDEN}、
 * {@code AUTHENTICATION_UNAVAILABLE}）に置き換えて、Controller と同じエラーハンドラーに渡す。Spring Security の型は、ここより先（common）に出さない。
 */
@RequiredArgsConstructor
class SecurityProblemResponder implements AuthenticationEntryPoint, AccessDeniedHandler, AuthenticationFailureHandler {

    /**
     * Controller のエラーハンドラー（{@code @RestControllerAdvice}）に例外を渡す、Spring MVC の例外の解決
     */
    @NonNull
    private final HandlerExceptionResolver resolver;

    /**
     * 認証がない・無効なときに 401 を返す。
     *
     * @param request       リクエスト
     * @param response      応答
     * @param authException 認証の失敗
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) {
        // RFC 6750：Bearer の token が必要なことを示す
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        resolver.resolveException(request, response, null, BusinessException.of(CommonErrorCode.UNAUTHENTICATED));
    }

    /**
     * token の検証の失敗を返す。
     *
     * <p>IdP に届かず検証できないときは、再試行できる 503 を返す。token が無効なときは、{@link #commence} と同じ 401 を返す。
     *
     * @param request   リクエスト
     * @param response  応答
     * @param exception 認証の失敗
     */
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) {
        // token の誤りでなく、検証の処理の失敗（IdP の設定や署名の鍵を取得できない）。token は直さなくてよいので、WWW-Authenticate を付けない
        if (exception instanceof AuthenticationServiceException) {
            resolver.resolveException(request, response, null,
                    SystemException.of("IdP に届かず、token を検証できない", exception, CommonErrorCode.AUTHENTICATION_UNAVAILABLE));
            return;
        }
        commence(request, response, exception);
    }

    /**
     * 権限がないときに 403 を返す。
     *
     * @param request               リクエスト
     * @param response              応答
     * @param accessDeniedException 認可の失敗
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) {
        resolver.resolveException(request, response, null, BusinessException.of(CommonErrorCode.FORBIDDEN));
    }
}

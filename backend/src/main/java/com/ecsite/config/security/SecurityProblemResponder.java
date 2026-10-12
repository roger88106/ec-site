package com.ecsite.config.security;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 認証・認可の失敗を、ほかのエラーと同じ Problem Details で返す（plan 003 第 3.3 節、constitution 9）。<br />
 * filter で起きた失敗を、業務の例外（{@code UNAUTHENTICATED}、{@code FORBIDDEN}）に置き換えて、
 * Controller と同じエラーハンドラーに渡す。Spring Security の型は、ここより先（common）に出さない（constitution 10.2）。
 */
class SecurityProblemResponder implements AuthenticationEntryPoint, AccessDeniedHandler {

    /**
     * Controller のエラーハンドラー（{@code @RestControllerAdvice}）を呼ぶ口
     */
    private final HandlerExceptionResolver resolver;

    /**
     * @param resolver Spring MVC の例外の解決
     */
    SecurityProblemResponder(HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * 認証がない・無効：401
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) {
        // RFC 6750：Bearer の token が要ることを示す
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        resolver.resolveException(request, response, null, BusinessException.of(CommonErrorCode.UNAUTHENTICATED));
    }

    /**
     * 権限がない：403
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) {
        resolver.resolveException(request, response, null, BusinessException.of(CommonErrorCode.FORBIDDEN));
    }
}

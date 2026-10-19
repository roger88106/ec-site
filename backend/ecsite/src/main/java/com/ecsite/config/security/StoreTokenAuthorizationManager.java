package com.ecsite.config.security;

import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.function.Supplier;

/**
 * 店舗 API の認可。{@code store.yaml} で会員の token が要る操作は、ログインしていれば許す。<br />
 * token の要らない操作は許し、{@code store.yaml} にない要求は拒否する（認証がなければ 401、あれば 403）。
 */
class StoreTokenAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    /**
     * 許可
     */
    private static final AuthorizationDecision PERMIT = new AuthorizationDecision(true);

    /**
     * 拒否
     */
    private static final AuthorizationDecision DENY = new AuthorizationDecision(false);

    /**
     * 操作ごとの token の要否
     */
    private final StoreOperationSecurity security;

    /**
     * @param security 操作ごとの token の要否
     */
    StoreTokenAuthorizationManager(StoreOperationSecurity security) {
        this.security = security;
    }

    /**
     * 要求のメソッドとパスで token の要否を求め、要るならログインしているかで決める。{@code store.yaml} にない要求は拒否する。
     */
    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
        var request = context.getRequest();
        var path = request.getRequestURI().substring(request.getContextPath().length());
        return security.requiresToken(request.getMethod(), path)
                .map(requiresToken -> requiresToken
                        ? AuthenticatedAuthorizationManager.<RequestAuthorizationContext>authenticated().authorize(authentication, context)
                        : PERMIT)
                .orElse(DENY);
    }
}

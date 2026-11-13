package com.ecsite.config.security;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.function.Supplier;

/**
 * 店舗 API の認可
 *
 * <p>{@code store.yaml} で会員の token が必要な操作は、ログインしていれば許す。
 * token の要らない操作は許し、{@code store.yaml} にないリクエストは拒否する（認証がなければ 401、あれば 403）。
 */
@RequiredArgsConstructor
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
     * ログインしているかの判定
     */
    private static final AuthenticatedAuthorizationManager<RequestAuthorizationContext> LOGGED_IN =
            AuthenticatedAuthorizationManager.authenticated();

    /**
     * 操作ごとの token の要否
     */
    @NonNull
    private final StoreOperationSecurity security;

    /**
     * リクエストのメソッドとパスで token の要否を求め、必要ならログインしているかで決める。
     *
     * <p>{@code store.yaml} にないリクエストは拒否する。
     *
     * @param authentication 認証
     * @param context        リクエストの認可の文脈
     * @return 認可の結果
     */
    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
        var request = context.getRequest();
        return security.requiresToken(request.getMethod(), RequestPaths.pathOf(request))
                .map(requiresToken -> requiresToken
                        ? LOGGED_IN.authorize(authentication, context)
                        : PERMIT)
                .orElse(DENY);
    }
}

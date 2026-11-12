package com.ecsite.config.security;

import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.function.Supplier;

/**
 * 管理 API の認可
 *
 * <p>{@code admin.yaml} の {@code x-required-permission} の権限を、token が持つかで決める。
 * {@code admin.yaml} にないリクエストは、権限によらず拒否する。権限が {@code authenticated} の操作は、ログインしていれば許す。
 * 認証のないリクエストは拒否され、401 になる（403 にするのは、認証のある運営者だけ）。
 */
class AdminPermissionAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

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
     * 権限ではなく、ログインしていればよい操作を表す値
     */
    static final String AUTHENTICATED = "authenticated";

    /**
     * 操作ごとに必要な権限
     */
    private final AdminOperationPermissions permissions;

    /**
     * コンストラクタ。
     *
     * @param permissions 操作ごとに必要な権限
     */
    AdminPermissionAuthorizationManager(AdminOperationPermissions permissions) {
        this.permissions = permissions;
    }

    /**
     * リクエストのメソッドとパスに必要な権限を求め、token がその権限を持てば許す。{@code admin.yaml} にないリクエストは拒否する。
     */
    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
        var request = context.getRequest();
        return permissions.requiredPermission(request.getMethod(), RequestPaths.pathOf(request))
                .map(permission -> permission.equals(AUTHENTICATED)
                        ? LOGGED_IN.authorize(authentication, context)
                        : AuthorityAuthorizationManager.<RequestAuthorizationContext>hasAuthority(permission)
                                .authorize(authentication, context))
                .orElse(DENY);
    }
}

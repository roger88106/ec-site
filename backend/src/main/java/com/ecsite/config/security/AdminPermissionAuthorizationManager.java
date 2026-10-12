package com.ecsite.config.security;

import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.function.Supplier;

/**
 * 管理 API の認可（plan 003 第 3.2 節）。{@code admin.yaml} の {@code x-required-permission} の権限を、token が持つかで決める。<br />
 * {@code admin.yaml} にない要求は、権限によらず拒否する（NFR-3）。
 * 認証のない要求は拒否され、401 になる（403 にするのは、認証のある運営者だけ）。
 */
class AdminPermissionAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    /**
     * 拒否
     */
    private static final AuthorizationDecision DENY = new AuthorizationDecision(false);

    /**
     * 操作ごとに必要な権限
     */
    private final AdminOperationPermissions permissions;

    /**
     * @param permissions 操作ごとに必要な権限
     */
    AdminPermissionAuthorizationManager(AdminOperationPermissions permissions) {
        this.permissions = permissions;
    }

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
        var request = context.getRequest();
        var path = request.getRequestURI().substring(request.getContextPath().length());
        return permissions.requiredPermission(request.getMethod(), path)
                .map(permission -> AuthorityAuthorizationManager.<RequestAuthorizationContext>hasAuthority(permission)
                        .authorize(authentication, context))
                .orElse(DENY);
    }
}

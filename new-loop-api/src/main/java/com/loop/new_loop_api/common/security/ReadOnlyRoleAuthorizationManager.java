package com.loop.new_loop_api.common.security;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.function.Supplier;

/** Default rule of the app chain: any authenticated user, but read-only roles only on their allowlist. */
public class ReadOnlyRoleAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
        var auth = authentication.get();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof AuthenticatedUser user)) {
            return new AuthorizationDecision(false);
        }
        var request = context.getRequest();
        var path    = request.getRequestURI().substring(request.getContextPath().length());
        return new AuthorizationDecision(ReadOnlyRoleAccessPolicy.allows(user.role(), request.getMethod(), path));
    }
}

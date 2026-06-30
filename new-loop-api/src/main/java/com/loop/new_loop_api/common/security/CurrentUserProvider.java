package com.loop.new_loop_api.common.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Reads the authenticated user from the SecurityContext for use in business logic. */
@Component
public class CurrentUserProvider {

    public Optional<AuthenticatedUser> current() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }
}

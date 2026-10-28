package com.loop.new_loop_api.performance.domain;

import com.loop.new_loop_api.common.security.AuthenticatedUser;
import com.loop.new_loop_api.performance.exception.MetricsAccessDeniedException;
import com.loop.new_loop_api.users.entity.Role;

import java.util.UUID;

/**
 * Which branches a metrics request may see. Admins see everything (or the branch they pick); a
 * supervisor only ever sees their own branch, whatever they ask for. Dispenser movements whose route
 * had no known branch belong to no branch, so only the "all branches" scope includes them.
 */
public record MetricsScope(boolean allBranches, UUID branchId) {

    /** {@code user} is null when nobody is authenticated. */
    public static MetricsScope resolve(AuthenticatedUser user, UUID requestedBranchId) {
        if (user == null) throw MetricsAccessDeniedException.notAllowed();
        if (user.role() == Role.ADMIN) {
            return requestedBranchId == null ? new MetricsScope(true, null) : new MetricsScope(false, requestedBranchId);
        }
        if (user.role() == Role.SUPERVISOR) {
            if (user.branchId() == null) throw MetricsAccessDeniedException.supervisorWithoutBranch();
            return new MetricsScope(false, user.branchId());
        }
        throw MetricsAccessDeniedException.notAllowed();
    }
}

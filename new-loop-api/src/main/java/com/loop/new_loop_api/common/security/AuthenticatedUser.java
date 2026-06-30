package com.loop.new_loop_api.common.security;

import com.loop.new_loop_api.users.entity.Role;

import java.util.UUID;

/**
 * Principal stored in the SecurityContext after a valid JWT is processed.
 * Carries the data needed for auditing the current user's actions.
 */
public record AuthenticatedUser(UUID id, String username, String name, Role role) {
}

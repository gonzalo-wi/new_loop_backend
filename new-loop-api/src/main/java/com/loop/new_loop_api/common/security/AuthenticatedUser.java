package com.loop.new_loop_api.common.security;

import com.loop.new_loop_api.users.entity.Role;

import java.util.UUID;


public record AuthenticatedUser(UUID id, String username, String name, Role role) {
}

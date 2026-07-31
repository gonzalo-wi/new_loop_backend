package com.loop.new_loop_api.users.exception;

import com.loop.new_loop_api.users.entity.Role;

public class BranchRequiredForRoleException extends RuntimeException {
    public BranchRequiredForRoleException(Role role) {
        super("A branch is required for role " + role);
    }
}

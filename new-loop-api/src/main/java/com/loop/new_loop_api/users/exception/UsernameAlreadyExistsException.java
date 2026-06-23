package com.loop.new_loop_api.users.exception;

import com.loop.new_loop_api.common.exception.ConflictException;

public class UsernameAlreadyExistsException extends ConflictException {
    public UsernameAlreadyExistsException(String username) {
        super("Username already exists: " + username);
    }
}

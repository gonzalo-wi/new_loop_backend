package com.loop.new_loop_api.users.exception;

import com.loop.new_loop_api.common.exception.NotFoundException;

import java.util.UUID;

public class UserNotFoundException extends NotFoundException {
    public UserNotFoundException(UUID id) {
        super("User not found with id: " + id);
    }
}

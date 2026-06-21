package com.loop.new_loop_api.branches.exception;

import com.loop.new_loop_api.common.exception.ConflictException;

public class BranchCodeAlreadyExistsException extends ConflictException {
    public BranchCodeAlreadyExistsException(String code) {
        super("Branch code already exists: " + code);
    }
}

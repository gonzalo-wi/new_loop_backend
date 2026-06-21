package com.loop.new_loop_api.branches.exception;

import com.loop.new_loop_api.common.exception.NotFoundException;

import java.util.UUID;

public class BranchNotFoundException extends NotFoundException {
    public BranchNotFoundException(UUID id) {
        super("Branch not found with id: " + id);
    }
}

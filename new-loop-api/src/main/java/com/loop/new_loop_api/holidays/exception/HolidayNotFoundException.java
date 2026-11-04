package com.loop.new_loop_api.holidays.exception;

import com.loop.new_loop_api.common.exception.NotFoundException;

import java.util.UUID;

public class HolidayNotFoundException extends NotFoundException {
    public HolidayNotFoundException(UUID id) {
        super("Holiday not found with id: " + id);
    }
}

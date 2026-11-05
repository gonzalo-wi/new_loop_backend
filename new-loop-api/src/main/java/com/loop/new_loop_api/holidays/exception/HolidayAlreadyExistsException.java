package com.loop.new_loop_api.holidays.exception;

import com.loop.new_loop_api.common.exception.ConflictException;

import java.time.LocalDate;

public class HolidayAlreadyExistsException extends ConflictException {
    public HolidayAlreadyExistsException(LocalDate date) {
        super("The date is already a holiday: " + date);
    }
}

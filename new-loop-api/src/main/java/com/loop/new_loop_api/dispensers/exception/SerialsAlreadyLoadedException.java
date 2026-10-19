package com.loop.new_loop_api.dispensers.exception;

import com.loop.new_loop_api.common.exception.ConflictException;

import java.time.LocalDate;
import java.util.List;

public class SerialsAlreadyLoadedException extends ConflictException {
    public SerialsAlreadyLoadedException(List<String> serials, String routeCode, LocalDate date) {
        super("These dispensers already went out in an earlier load of route " + routeCode + " on " + date
                + ": " + serials + ". A reload must only carry new dispensers.");
    }
}

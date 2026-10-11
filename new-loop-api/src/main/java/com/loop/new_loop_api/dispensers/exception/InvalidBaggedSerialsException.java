package com.loop.new_loop_api.dispensers.exception;

import java.util.List;

public class InvalidBaggedSerialsException extends RuntimeException {

    private InvalidBaggedSerialsException(String message) {
        super(message);
    }

    public static InvalidBaggedSerialsException onlyForUnload() {
        return new InvalidBaggedSerialsException("Bagged serials are only allowed on UNLOAD movements");
    }

    public static InvalidBaggedSerialsException notScanned(List<String> serials) {
        return new InvalidBaggedSerialsException("Bagged serials must be among the scanned serials: " + serials);
    }
}

package com.loop.new_loop_api.common.exception;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/** Lets services translate a specific unique-index violation into their own domain exception. */
public final class UniqueConstraintViolation {

    private UniqueConstraintViolation() {
    }

    public static boolean isCausedBy(DataIntegrityViolationException ex, String constraintName) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation) {
                return constraintName.equalsIgnoreCase(violation.getConstraintName());
            }
            cause = cause.getCause();
        }
        return false;
    }
}

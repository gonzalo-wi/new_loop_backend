package com.loop.new_loop_api.performance.exception;

import com.loop.new_loop_api.common.exception.ForbiddenException;

public class MetricsAccessDeniedException extends ForbiddenException {

    private MetricsAccessDeniedException(String message) {
        super(message);
    }

    public static MetricsAccessDeniedException notAllowed() {
        return new MetricsAccessDeniedException("Only users with role ADMIN or SUPERVISOR can see performance metrics");
    }

    public static MetricsAccessDeniedException importNotAllowed() {
        return new MetricsAccessDeniedException("Only users with role ADMIN can import the audit history");
    }

    public static MetricsAccessDeniedException supervisorWithoutBranch() {
        return new MetricsAccessDeniedException("Your user has no branch assigned: ask an admin to assign one to see performance metrics");
    }
}

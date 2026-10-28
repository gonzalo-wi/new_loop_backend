package com.loop.new_loop_api.performance.domain;

/**
 * Operators type only the route number in the dispenser screen ("179") while routes are named like
 * "Rto 179", so a dispenser movement is matched to its route by the number alone, ignoring letters.
 */
public final class RouteNumber {

    // Longer digit runs are not route numbers and would overflow a long.
    private static final int MAX_DIGITS = 18;

    private RouteNumber() {
    }

    /** The number in a route code, or null when it has no digits. */
    public static Long of(String code) {
        if (code == null) return null;
        var digits = code.replaceAll("\\D", "");
        if (digits.isEmpty() || digits.length() > MAX_DIGITS) return null;
        return Long.parseLong(digits);
    }
}

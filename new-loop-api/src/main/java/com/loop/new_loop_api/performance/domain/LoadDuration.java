package com.loop.new_loop_api.performance.domain;

/**
 * Plausibility rule for the dispenser load duration the app measures. A form left open for an hour
 * or more is a pause, not a load, so it is dropped rather than skewing the averages.
 */
public final class LoadDuration {

    public static final int MAX_PLAUSIBLE_SECONDS = 3600;

    private LoadDuration() {
    }

    /** The duration to store, or null when it is missing or not believable. */
    public static Integer accept(Integer seconds) {
        if (seconds == null || seconds < 0 || seconds >= MAX_PLAUSIBLE_SECONDS) return null;
        return seconds;
    }
}

package com.loop.new_loop_api.common.metrics;

/** Micrometer tag key constants shared across metrics, to avoid repeated magic strings. */
public final class MetricsTags {

    public static final String EXCEPTION = "exception";
    public static final String ENDPOINT  = "endpoint";
    public static final String OPERATION = "operation";
    public static final String RESULT    = "result";
    public static final String MOVEMENT_TYPE = "movement_type";
    public static final String INTEGRATION    = "integration";

    private MetricsTags() {
    }
}

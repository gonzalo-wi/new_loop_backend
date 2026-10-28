package com.loop.new_loop_api.performance.repository;

import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;
import com.loop.new_loop_api.performance.domain.MetricsRange;
import com.loop.new_loop_api.performance.domain.MetricsScope;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;

import java.util.UUID;

/**
 * What a metrics query covers. {@code byDay} splits each user's figures per event date (the trend);
 * otherwise there is one row per user for the whole range. Null filters mean "any".
 */
public record MetricsQuery(MetricsScope scope, MetricsRange range, UUID responsibleUserId,
                           ControlType controlType, DispenserMovementType movementType, boolean byDay) {
}

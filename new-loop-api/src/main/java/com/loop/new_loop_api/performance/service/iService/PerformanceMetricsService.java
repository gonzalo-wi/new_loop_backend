package com.loop.new_loop_api.performance.service.iService;

import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;
import com.loop.new_loop_api.performance.dto.PerformanceSummaryResponse;
import com.loop.new_loop_api.performance.dto.UserTrendResponse;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;

import java.time.LocalDate;
import java.util.UUID;

public interface PerformanceMetricsService {

    PerformanceSummaryResponse getSummary(LocalDate from, LocalDate to, UUID branchId, UUID userId,
                                          ControlType controlType, DispenserMovementType movementType);

    UserTrendResponse getUserTrend(UUID userId, LocalDate from, LocalDate to, UUID branchId,
                                   ControlType controlType, DispenserMovementType movementType);
}

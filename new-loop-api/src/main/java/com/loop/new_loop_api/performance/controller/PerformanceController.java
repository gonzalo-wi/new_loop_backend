package com.loop.new_loop_api.performance.controller;

import com.loop.new_loop_api.common.response.ApiResponse;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;
import com.loop.new_loop_api.performance.dto.ImportReport;
import com.loop.new_loop_api.performance.dto.PerformanceSummaryResponse;
import com.loop.new_loop_api.performance.dto.UserTrendResponse;
import com.loop.new_loop_api.performance.service.iService.AuditHistoryImportService;
import com.loop.new_loop_api.performance.service.iService.PerformanceMetricsService;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/** Who may see what (admins and supervisors, per branch) is enforced by the service. */
@RestController
@RequestMapping("/performance")
@RequiredArgsConstructor
public class PerformanceController {

    private final PerformanceMetricsService performanceMetricsService;
    private final AuditHistoryImportService auditHistoryImportService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<PerformanceSummaryResponse>> getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) ControlType controlType,
            @RequestParam(required = false) DispenserMovementType movementType) {
        return ResponseEntity.ok(ApiResponse.ok(
                performanceMetricsService.getSummary(from, to, branchId, userId, controlType, movementType),
                "Performance metrics retrieved successfully"));
    }

    /** One-time import of the stock-control history still in the audit log; safe to run again. Admins only. */
    @PostMapping("/import")
    public ResponseEntity<ApiResponse<ImportReport>> importHistory() {
        return ResponseEntity.ok(ApiResponse.ok(auditHistoryImportService.importHistory(),
                "Audit history imported successfully"));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<UserTrendResponse>> getUserTrend(
            @PathVariable UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) ControlType controlType,
            @RequestParam(required = false) DispenserMovementType movementType) {
        return ResponseEntity.ok(ApiResponse.ok(
                performanceMetricsService.getUserTrend(userId, from, to, branchId, controlType, movementType),
                "User performance trend retrieved successfully"));
    }
}

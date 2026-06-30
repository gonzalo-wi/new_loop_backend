package com.loop.new_loop_api.integrations.common.controller;

import com.loop.new_loop_api.common.response.ApiResponse;
import com.loop.new_loop_api.integrations.aguas.service.iService.AguasIntegrationService;
import com.loop.new_loop_api.integrations.common.dto.IntegrationLogResponse;
import com.loop.new_loop_api.integrations.common.entity.IntegrationName;
import com.loop.new_loop_api.integrations.common.entity.IntegrationStatus;
import com.loop.new_loop_api.integrations.common.service.iService.IntegrationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/integration-logs")
@RequiredArgsConstructor
public class IntegrationLogController {

    private final IntegrationLogService   integrationLogService;
    private final AguasIntegrationService aguasIntegrationService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<IntegrationLogResponse>>> getAll(
            @RequestParam(required = false) IntegrationName integrationName,
            @RequestParam(required = false) IntegrationStatus status,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        var page = integrationLogService.getAll(integrationName, status, entityId, from, to, pageable);
        return ResponseEntity.ok(ApiResponse.ok(page, "Integration logs retrieved successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<IntegrationLogResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(integrationLogService.getById(id)));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<ApiResponse<IntegrationLogResponse>> retry(@PathVariable UUID id) {
        aguasIntegrationService.retry(id);
        return ResponseEntity.ok(ApiResponse.ok(integrationLogService.getById(id), "Integration retried"));
    }
}

package com.loop.new_loop_api.audit.controller;

import com.loop.new_loop_api.audit.dto.AuditLogResponse;
import com.loop.new_loop_api.audit.service.iService.AuditService;
import com.loop.new_loop_api.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Audit Logs")
public class AuditController {

    private final AuditService auditService;

    @Parameters({
            @Parameter(name = "page",       example = "0"),
            @Parameter(name = "size",       example = "50"),
            @Parameter(name = "sort",       example = "createdAt,desc"),
            @Parameter(name = "entityName", example = "Branch"),
            @Parameter(name = "action",     example = "CREATE_BRANCH"),
            @Parameter(name = "entityId",   example = "550e8400-e29b-41d4-a716-446655440000"),
            @Parameter(name = "from",       example = "2026-06-01T00:00:00"),
            @Parameter(name = "to",         example = "2026-06-30T23:59:59")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AuditLogResponse>>> getAll(
            @RequestParam(required = false) String entityName,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        var result = auditService.getAll(entityName, action, entityId, from, to, pageable);
        return ResponseEntity.ok(ApiResponse.ok(result, "Audit logs retrieved successfully"));
    }
}

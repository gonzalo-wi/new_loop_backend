package com.loop.new_loop_api.stockcontrols.controller;

import com.loop.new_loop_api.common.response.ApiResponse;
import com.loop.new_loop_api.stockcontrols.dto.ArrivalsSummaryResponse;
import com.loop.new_loop_api.stockcontrols.dto.CreateStockControlRequest;
import com.loop.new_loop_api.stockcontrols.dto.StockControlResponse;
import com.loop.new_loop_api.stockcontrols.dto.UpdateStockControlRequest;
import com.loop.new_loop_api.stockcontrols.service.iService.StockControlService;
import com.loop.new_loop_api.stockcontrols.entity.ControlStatus;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/stock-controls")
@RequiredArgsConstructor
public class StockControlController {

    private final StockControlService stockControlService;

    @PostMapping
    public ResponseEntity<ApiResponse<StockControlResponse>> createControl(
            @Valid @RequestBody CreateStockControlRequest request) {
        var response = stockControlService.createControl(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Stock control created successfully"));
    }

    
    @GetMapping
    public ResponseEntity<ApiResponse<Page<StockControlResponse>>> getAllControls(
            @RequestParam(required = false) ControlType type,
            @RequestParam(required = false) ControlStatus status,
            @RequestParam(required = false) UUID routeId,
            @RequestParam(required = false) UUID controllerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        var page = stockControlService.getAllControls(type, status, routeId, controllerId, from, to, pageable);
        return ResponseEntity.ok(ApiResponse.ok(page, "Stock controls retrieved successfully"));
    }


    @GetMapping("/pending-arrivals")
    public ResponseEntity<ApiResponse<ArrivalsSummaryResponse>> getPendingArrivals(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) UUID branchId) {
        var summary = stockControlService.getPendingArrivals(date, branchId);
        return ResponseEntity.ok(ApiResponse.ok(summary, "Pending arrivals retrieved successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StockControlResponse>> getControlById(@PathVariable UUID id) {
        var response = stockControlService.getControlById(id);
        return ResponseEntity.ok(ApiResponse.ok(response, "Stock control retrieved successfully"));
    }

    @GetMapping("/remito")
    public ResponseEntity<byte[]> getRemitoByRouteAndDate(
            @RequestParam UUID routeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        var pdf = stockControlService.generateRemitoPdfByRouteAndDate(routeId, date);
        return remitoResponse(pdf, "remito-" + routeId + "-" + date + ".pdf");
    }

    @GetMapping("/{id}/remito")
    public ResponseEntity<byte[]> getRemitoById(@PathVariable UUID id) {
        var pdf = stockControlService.generateRemitoPdf(id);
        return remitoResponse(pdf, "remito-" + id + ".pdf");
    }

    private ResponseEntity<byte[]> remitoResponse(byte[] pdf, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(pdf);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<StockControlResponse>> updateControl(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStockControlRequest request) {
        var response = stockControlService.updateControl(id, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Stock control updated successfully"));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<StockControlResponse>> approveControl(@PathVariable UUID id) {
        var response = stockControlService.approveControl(id);
        return ResponseEntity.ok(ApiResponse.ok(response, "Stock control approved successfully"));
    }
}

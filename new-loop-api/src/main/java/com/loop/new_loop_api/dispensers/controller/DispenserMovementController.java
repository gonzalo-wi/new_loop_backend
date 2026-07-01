package com.loop.new_loop_api.dispensers.controller;

import com.loop.new_loop_api.common.response.ApiResponse;
import com.loop.new_loop_api.dispensers.dto.CreateDispenserMovementRequest;
import com.loop.new_loop_api.dispensers.dto.DispenserMovementResponse;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementStatus;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;
import com.loop.new_loop_api.dispensers.service.iService.DispenserMovementService;
import com.loop.new_loop_api.integrations.aguas.service.iService.AguasEquipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/dispenser-movements")
@RequiredArgsConstructor
public class DispenserMovementController {

    private final DispenserMovementService dispenserMovementService;
    private final AguasEquipmentService    aguasEquipmentService;

    @PostMapping
    public ResponseEntity<ApiResponse<DispenserMovementResponse>> createMovement(
            @Valid @RequestBody CreateDispenserMovementRequest request) {
        var response = dispenserMovementService.createMovement(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Dispenser movement registered successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<DispenserMovementResponse>>> getAllMovements(
            @RequestParam(required = false) DispenserMovementType type,
            @RequestParam(required = false) String routeCode,
            @RequestParam(required = false) DispenserMovementStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        var page = dispenserMovementService.getAllMovements(type, routeCode, status, from, to, pageable);
        return ResponseEntity.ok(ApiResponse.ok(page, "Dispenser movements retrieved successfully"));
    }

    @GetMapping("/aguas/locations")
    public ResponseEntity<ApiResponse<Object>> getAguasLocations() {
        return ResponseEntity.ok(ApiResponse.ok(aguasEquipmentService.getDestinationLocations()));
    }

    @GetMapping("/aguas/states")
    public ResponseEntity<ApiResponse<Object>> getAguasStates() {
        return ResponseEntity.ok(ApiResponse.ok(aguasEquipmentService.getDestinationStates()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DispenserMovementResponse>> getMovementById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(dispenserMovementService.getMovementById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DispenserMovementResponse>> updateMovement(
            @PathVariable UUID id,
            @Valid @RequestBody CreateDispenserMovementRequest request) {
        var response = dispenserMovementService.updateMovement(id, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Dispenser movement updated successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<DispenserMovementResponse>> cancelMovement(@PathVariable UUID id) {
        var response = dispenserMovementService.cancelMovement(id);
        return ResponseEntity.ok(ApiResponse.ok(response, "Dispenser movement cancelled successfully"));
    }
}

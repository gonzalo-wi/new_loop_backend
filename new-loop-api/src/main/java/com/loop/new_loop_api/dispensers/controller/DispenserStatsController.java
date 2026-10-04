package com.loop.new_loop_api.dispensers.controller;

import com.loop.new_loop_api.common.response.ApiResponse;
import com.loop.new_loop_api.dispensers.dto.DispenserStatsResponse;
import com.loop.new_loop_api.dispensers.service.iService.DispenserStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/dispenser-movements/stats")
@RequiredArgsConstructor
public class DispenserStatsController {

    private final DispenserStatsService dispenserStatsService;

    @GetMapping
    public ResponseEntity<ApiResponse<DispenserStatsResponse>> getStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok(dispenserStatsService.getStats(from, to),
                "Dispenser movement stats retrieved successfully"));
    }
}

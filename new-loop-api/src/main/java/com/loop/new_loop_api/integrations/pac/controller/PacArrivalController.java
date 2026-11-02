package com.loop.new_loop_api.integrations.pac.controller;

import com.loop.new_loop_api.common.response.ApiResponse;
import com.loop.new_loop_api.integrations.pac.dto.PacArrivalResponse;
import com.loop.new_loop_api.integrations.pac.service.iService.PacArrivalService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Read-only surface for PAC, behind its own HTTP Basic chain (see SecurityConfig). The caller is never read. */
@RestController
@RequestMapping("/integrations/pac")
@RequiredArgsConstructor
public class PacArrivalController {

    private static final String ARRIVALS_RETRIEVED_MESSAGE = "Arrivals retrieved successfully";

    private final PacArrivalService pacArrivalService;

    @GetMapping("/arrivals")
    public ResponseEntity<ApiResponse<List<PacArrivalResponse>>> getArrivals(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.ok(pacArrivalService.getArrivals(date), ARRIVALS_RETRIEVED_MESSAGE));
    }
}

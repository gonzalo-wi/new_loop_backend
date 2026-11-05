package com.loop.new_loop_api.holidays.controller;

import com.loop.new_loop_api.common.response.ApiResponse;
import com.loop.new_loop_api.holidays.dto.CreateHolidayRequest;
import com.loop.new_loop_api.holidays.dto.HolidayResponse;
import com.loop.new_loop_api.holidays.service.iService.HolidayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Holiday calendar; creating and deleting are restricted to ADMIN in SecurityConfig. */
@RestController
@RequestMapping("/holidays")
@RequiredArgsConstructor
public class HolidayController {

    private final HolidayService holidayService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<HolidayResponse>>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from) {
        return ResponseEntity.ok(ApiResponse.ok(holidayService.list(from)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<HolidayResponse>> create(@Valid @RequestBody CreateHolidayRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(holidayService.create(request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        holidayService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

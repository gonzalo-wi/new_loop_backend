package com.loop.new_loop_api.settlements.controller;

import com.loop.new_loop_api.common.response.ApiResponse;
import com.loop.new_loop_api.settlements.dto.DriverSettlementResponse;
import com.loop.new_loop_api.settlements.service.iService.DriverReceiptService;
import com.loop.new_loop_api.settlements.service.iService.DriverSettlementService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Drivers only (SecurityConfig and the service); the driver comes from the token, never from the request. */
@RestController
@RequestMapping("/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final DriverSettlementService driverSettlementService;
    private final DriverReceiptService driverReceiptService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<DriverSettlementResponse>> getMySettlement() {
        return ResponseEntity.ok(ApiResponse.ok(driverSettlementService.getMySettlement(),
                "Driver settlement retrieved successfully"));
    }

    /** The request is read by hand (see the reader) so the error order and the Spanish messages stay ours. */
    @PostMapping("/me/receipts")
    public ResponseEntity<Void> uploadReceipt(HttpServletRequest request) {
        driverReceiptService.upload(ReceiptUploadRequestReader.read(request));
        return ResponseEntity.noContent().build();
    }
}

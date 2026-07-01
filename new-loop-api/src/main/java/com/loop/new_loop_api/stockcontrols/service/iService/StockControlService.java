package com.loop.new_loop_api.stockcontrols.service.iService;

import com.loop.new_loop_api.stockcontrols.dto.ArrivalsSummaryResponse;
import com.loop.new_loop_api.stockcontrols.dto.CreateStockControlRequest;
import com.loop.new_loop_api.stockcontrols.dto.StockControlResponse;
import com.loop.new_loop_api.stockcontrols.dto.UpdateStockControlRequest;
import com.loop.new_loop_api.stockcontrols.entity.ControlStatus;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

public interface StockControlService {

    StockControlResponse createControl(CreateStockControlRequest request);
    Page<StockControlResponse> getAllControls(ControlType type, ControlStatus status, UUID routeId, UUID controllerId, LocalDate from, LocalDate to, Pageable pageable);
    StockControlResponse getControlById(UUID id);
    StockControlResponse updateControl(UUID id, UpdateStockControlRequest request);
    StockControlResponse approveControl(UUID id);
    ArrivalsSummaryResponse getPendingArrivals(LocalDate date, UUID branchId);
}

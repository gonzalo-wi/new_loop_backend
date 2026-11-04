package com.loop.new_loop_api.stockcontrols.service.iService;

import com.loop.new_loop_api.stockcontrols.dto.ArrivalsSummaryResponse;
import com.loop.new_loop_api.stockcontrols.dto.CorrectStockControlRequest;
import com.loop.new_loop_api.stockcontrols.dto.CreateStockControlRequest;
import com.loop.new_loop_api.stockcontrols.dto.RejectStockControlRequest;
import com.loop.new_loop_api.stockcontrols.dto.StockControlResponse;
import com.loop.new_loop_api.stockcontrols.dto.UpdateStockControlRequest;
import com.loop.new_loop_api.stockcontrols.entity.ControlStatus;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface StockControlService {

    StockControlResponse createControl(CreateStockControlRequest request);
    Page<StockControlResponse> getAllControls(ControlType type, ControlStatus status, UUID routeId, UUID controllerId, UUID branchId, LocalDate from, LocalDate to, LocalDate createdFrom, LocalDate createdTo, Pageable pageable);
    StockControlResponse getControlById(UUID id);
    StockControlResponse updateControl(UUID id, UpdateStockControlRequest request);
    StockControlResponse approveControl(UUID id);
    StockControlResponse rejectControl(UUID id, RejectStockControlRequest request);
    StockControlResponse correctControl(UUID id, CorrectStockControlRequest request);
    ArrivalsSummaryResponse getPendingArrivals(LocalDate date, UUID branchId);
    byte[] generateRemitoPdf(UUID id);
    StockControlResponse markRemitoPrinted(UUID id);
    byte[] generateRemitoPdfByRouteAndDate(UUID routeId, LocalDate date);
    /** Every remito in one PDF, one per page, in the given order. */
    byte[] generateRemitosPdf(List<UUID> ids);
    List<StockControlResponse> markRemitosPrinted(List<UUID> ids);
}

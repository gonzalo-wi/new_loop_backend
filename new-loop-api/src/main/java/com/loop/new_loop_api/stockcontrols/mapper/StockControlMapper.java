package com.loop.new_loop_api.stockcontrols.mapper;

import com.loop.new_loop_api.branches.entity.Branch;
import com.loop.new_loop_api.products.entity.Product;
import com.loop.new_loop_api.routes.entity.Route;
import com.loop.new_loop_api.stockcontrols.dto.CreateStockControlItemRequest;
import com.loop.new_loop_api.stockcontrols.dto.CreateStockControlRequest;
import com.loop.new_loop_api.stockcontrols.dto.PendingArrivalResponse;
import com.loop.new_loop_api.stockcontrols.dto.StockControlItemResponse;
import com.loop.new_loop_api.stockcontrols.dto.StockControlResponse;
import com.loop.new_loop_api.stockcontrols.dto.UpdateStockControlRequest;
import com.loop.new_loop_api.stockcontrols.entity.StockControl;
import com.loop.new_loop_api.stockcontrols.entity.StockControlItem;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class StockControlMapper {

    public StockControl toEntity(CreateStockControlRequest request, Branch branch, Route route, LocalDate controlDate) {
        return StockControl.builder()
                .type(request.getType())
                .branch(branch)
                .route(route)
                .controllerId(request.getControllerId())
                .controlDate(controlDate)
                .truckOrdered(request.getTruckOrdered() != null ? request.getTruckOrdered() : true)
                .observations(request.getObservations())
                .build();
    }

    public StockControlResponse toResponse(StockControl control) {
        var items = control.getItems().stream()
                .map(this::itemToResponse)
                .toList();

        return StockControlResponse.builder()
                .id(control.getId())
                .type(control.getType())
                .status(control.getStatus())
                .branchId(control.getBranch().getId())
                .branchName(control.getBranch().getName())
                .routeId(control.getRoute().getId())
                .routeCode(control.getRoute().getCode())
                .controllerId(control.getControllerId())
                .controlDate(control.getControlDate())
                .truckOrdered(control.getTruckOrdered())
                .observations(control.getObservations())
                .items(items)
                .confirmedAt(control.getConfirmedAt())
                .approvedAt(control.getApprovedAt())
                .aguasFormulario(control.getAguasFormulario())
                .aguasNroRemito(control.getAguasNroRemito())
                .createdAt(control.getCreatedAt())
                .updatedAt(control.getUpdatedAt())
                .build();
    }

    public void updateEntity(UpdateStockControlRequest request, StockControl control) {
        if (request.getControllerId()  != null) control.setControllerId(request.getControllerId());
        if (request.getControlDate()   != null) control.setControlDate(request.getControlDate());
        if (request.getTruckOrdered()  != null) control.setTruckOrdered(request.getTruckOrdered());
        if (request.getObservations()  != null) control.setObservations(request.getObservations());
    }

    public StockControlItem toItem(CreateStockControlItemRequest request, Product product, StockControl control) {
        return StockControlItem.builder()
                .stockControl(control)
                .product(product)
                .totalQuantity(request.getTotalQuantity())
                .fullQuantity(request.getFullQuantity())
                .exchangeQuantity(request.getExchangeQuantity())
                .observations(request.getObservations())
                .build();
    }

    public PendingArrivalResponse toPendingArrival(StockControl control) {
        return PendingArrivalResponse.builder()
                .routeId(control.getRoute().getId())
                .routeCode(control.getRoute().getCode())
                .branchId(control.getBranch().getId())
                .branchName(control.getBranch().getName())
                .exitControlId(control.getId())
                .controlDate(control.getControlDate())
                .build();
    }

    public StockControlItemResponse itemToResponse(StockControlItem item) {
        return StockControlItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .productCode(item.getProduct().getCode())
                .productName(item.getProduct().getName())
                .productUnit(item.getProduct().getUnit())
                .totalQuantity(item.getTotalQuantity())
                .fullQuantity(item.getFullQuantity())
                .exchangeQuantity(item.getExchangeQuantity())
                .differenceQuantity(item.getDifferenceQuantity())
                .observations(item.getObservations())
                .build();
    }
}

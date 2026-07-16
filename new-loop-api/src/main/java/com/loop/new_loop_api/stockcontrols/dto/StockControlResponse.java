package com.loop.new_loop_api.stockcontrols.dto;

import com.loop.new_loop_api.stockcontrols.entity.ControlStatus;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class StockControlResponse {

    private UUID                           id;
    private ControlType                    type;
    private ControlStatus                  status;
    private UUID                           branchId;
    private String                         branchName;
    private UUID                           routeId;
    private String                         routeCode;
    private UUID                           controllerId;
    private LocalDate                      controlDate;
    private Boolean                        truckOrdered;
    private String                         observations;
    private List<StockControlItemResponse> items;
    private LocalDateTime                  confirmedAt;
    private LocalDateTime                  approvedAt;
    private String                         aguasFormulario;
    private Long                           aguasNroRemito;
    private LocalDateTime                  createdAt;
    private LocalDateTime                  updatedAt;
}

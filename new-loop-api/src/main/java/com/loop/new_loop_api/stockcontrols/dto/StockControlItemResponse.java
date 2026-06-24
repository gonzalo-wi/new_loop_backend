package com.loop.new_loop_api.stockcontrols.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class StockControlItemResponse {

    private UUID id;
    private UUID productId;
    private String productCode;
    private String productName;
    private String productUnit;
    private Integer totalQuantity;
    private Integer fullQuantity;
    private Integer exchangeQuantity;
    private Integer differenceQuantity;
    private String observations;
}

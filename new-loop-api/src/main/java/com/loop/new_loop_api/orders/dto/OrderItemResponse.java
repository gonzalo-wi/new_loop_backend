package com.loop.new_loop_api.orders.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class OrderItemResponse {

    private UUID    id;
    private UUID    productId;
    private String  productCode;
    private String  productName;
    private Boolean allowsUnit;
    private Boolean allowsBulk;
    private Integer unitsPerBulk;
    private Integer unitQuantity;
    private Integer bulkQuantity;
}

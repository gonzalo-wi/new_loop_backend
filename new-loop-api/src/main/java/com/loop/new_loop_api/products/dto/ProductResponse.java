package com.loop.new_loop_api.products.dto;

import com.loop.new_loop_api.products.entity.ProductType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class ProductResponse {

    private UUID          id;
    private String        code;
    private String        name;
    private Integer       displayOrder;
    private String        description;
    private ProductType   type;
    private String        unit;
    private Integer       packQuantity;
    private Boolean       active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.loop.new_loop_api.orders.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class OrderableProductResponse {

    private UUID          id;
    private String        code;
    private String        name;
    private String        description;
    private Boolean       allowsUnit;
    private Boolean       allowsBulk;
    private Integer       unitsPerBulk;
    private Boolean       active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

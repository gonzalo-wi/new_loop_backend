package com.loop.new_loop_api.orders.dto;

import com.loop.new_loop_api.orders.entity.OrderStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class OrderResponse {

    private UUID                  id;
    private UUID                  routeId;
    private String                routeCode;
    private OrderStatus           status;
    private LocalDate             orderDate;
    private String                observations;
    private List<OrderItemResponse> items;
    private UUID                  startedBy;
    private String                startedByName;
    private LocalDateTime         startedAt;
    private UUID                  completedBy;
    private String                completedByName;
    private LocalDateTime         completedAt;
    private LocalDateTime         createdAt;
    private LocalDateTime         updatedAt;
}

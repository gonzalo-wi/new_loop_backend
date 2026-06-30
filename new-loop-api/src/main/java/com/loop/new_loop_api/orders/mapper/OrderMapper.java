package com.loop.new_loop_api.orders.mapper;

import com.loop.new_loop_api.orders.dto.CreateOrderItemRequest;
import com.loop.new_loop_api.orders.dto.CreateOrderRequest;
import com.loop.new_loop_api.orders.dto.OrderItemResponse;
import com.loop.new_loop_api.orders.dto.OrderResponse;
import com.loop.new_loop_api.orders.entity.Order;
import com.loop.new_loop_api.orders.entity.OrderItem;
import com.loop.new_loop_api.orders.entity.OrderableProduct;
import com.loop.new_loop_api.routes.entity.Route;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class OrderMapper {

    public Order toEntity(CreateOrderRequest request, Route route, LocalDate orderDate) {
        return Order.builder()
                .route(route)
                .orderDate(orderDate)
                .observations(request.getObservations())
                .build();
    }

    public OrderItem toItem(CreateOrderItemRequest request, OrderableProduct product, Order order) {
        return OrderItem.builder()
                .order(order)
                .product(product)
                .unitQuantity(request.getUnitQuantity())
                .bulkQuantity(request.getBulkQuantity())
                .build();
    }

    public OrderResponse toResponse(Order order) {
        var items = order.getItems().stream()
                .map(this::itemToResponse)
                .toList();

        return OrderResponse.builder()
                .id(order.getId())
                .routeId(order.getRoute().getId())
                .routeCode(order.getRoute().getCode())
                .status(order.getStatus())
                .orderDate(order.getOrderDate())
                .observations(order.getObservations())
                .items(items)
                .startedBy(order.getStartedBy())
                .startedByName(order.getStartedByName())
                .startedAt(order.getStartedAt())
                .completedBy(order.getCompletedBy())
                .completedByName(order.getCompletedByName())
                .completedAt(order.getCompletedAt())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    public OrderItemResponse itemToResponse(OrderItem item) {
        return OrderItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .productCode(item.getProduct().getCode())
                .productName(item.getProduct().getName())
                .allowsUnit(item.getProduct().getAllowsUnit())
                .allowsBulk(item.getProduct().getAllowsBulk())
                .unitsPerBulk(item.getProduct().getUnitsPerBulk())
                .unitQuantity(item.getUnitQuantity())
                .bulkQuantity(item.getBulkQuantity())
                .build();
    }
}

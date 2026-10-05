package com.loop.new_loop_api.orders;

import com.loop.new_loop_api.branches.entity.Branch;
import com.loop.new_loop_api.branches.repository.BranchRepository;
import com.loop.new_loop_api.common.exception.ConflictException;
import com.loop.new_loop_api.orders.dto.OrderResponse;
import com.loop.new_loop_api.orders.entity.Order;
import com.loop.new_loop_api.orders.entity.OrderStatus;
import com.loop.new_loop_api.orders.exception.OrderModifiedConcurrentlyException;
import com.loop.new_loop_api.orders.repository.OrderRepository;
import com.loop.new_loop_api.orders.service.iService.OrderService;
import com.loop.new_loop_api.routes.entity.Route;
import com.loop.new_loop_api.routes.repository.RouteRepository;
import com.loop.new_loop_api.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStartConcurrencyTest extends PostgresIntegrationTest {

    @Autowired private OrderService        orderService;
    @Autowired private OrderRepository     orderRepository;
    @Autowired private BranchRepository    branchRepository;
    @Autowired private RouteRepository     routeRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    private UUID orderId;

    @BeforeEach
    void setUp() {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var branch = branchRepository.save(Branch.builder().name("Branch " + suffix).code("B-" + suffix).active(true).build());
        var route  = routeRepository.save(Route.builder().code("Rto " + suffix).branch(branch).active(true).build());
        orderId    = orderRepository.save(Order.builder().route(route).orderDate(LocalDate.now()).build()).getId();
    }

    @Test
    void shouldLetOnlyOnePickerStartTheOrderWhenBothTryTogether() throws Exception {
        Callable<OrderResponse> start = () -> orderService.startOrder(orderId);

        var outcomes = runConcurrently(List.of(start, start));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(o -> !o.succeeded())
                .singleElement()
                .extracting(Outcome::error)
                .isInstanceOf(ConflictException.class);
        assertThat(orderRepository.findById(orderId)).get()
                .extracting(Order::getStatus).isEqualTo(OrderStatus.IN_PROGRESS);
    }

    @Test
    void shouldRejectStartWhenOrderWasStartedAfterItWasRead() {
        // Reproduces the race deterministically: this transaction reads the PENDING order, another one
        // starts it and commits, then this one tries to start its stale copy.
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            orderRepository.findById(orderId).orElseThrow();
            CompletableFuture.runAsync(() -> orderService.startOrder(orderId)).join();
            orderService.startOrder(orderId);
        })).isInstanceOf(OrderModifiedConcurrentlyException.class);

        assertThat(orderRepository.findById(orderId)).get()
                .extracting(Order::getVersion).isEqualTo(1L);
    }
}

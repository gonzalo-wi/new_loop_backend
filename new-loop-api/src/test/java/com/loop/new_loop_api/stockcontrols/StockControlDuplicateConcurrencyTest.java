package com.loop.new_loop_api.stockcontrols;

import com.loop.new_loop_api.branches.entity.Branch;
import com.loop.new_loop_api.branches.repository.BranchRepository;
import com.loop.new_loop_api.products.entity.Product;
import com.loop.new_loop_api.products.entity.ProductType;
import com.loop.new_loop_api.products.repository.ProductRepository;
import com.loop.new_loop_api.routes.entity.Route;
import com.loop.new_loop_api.routes.repository.RouteRepository;
import com.loop.new_loop_api.stockcontrols.dto.CreateStockControlItemRequest;
import com.loop.new_loop_api.stockcontrols.dto.CreateStockControlRequest;
import com.loop.new_loop_api.stockcontrols.dto.StockControlResponse;
import com.loop.new_loop_api.stockcontrols.dto.UpdateStockControlRequest;
import com.loop.new_loop_api.stockcontrols.entity.ControlStatus;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;
import com.loop.new_loop_api.stockcontrols.exception.DuplicateControlException;
import com.loop.new_loop_api.stockcontrols.repository.StockControlRepository;
import com.loop.new_loop_api.stockcontrols.service.iService.StockControlService;
import com.loop.new_loop_api.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

class StockControlDuplicateConcurrencyTest extends PostgresIntegrationTest {

    @Autowired private StockControlService stockControlService;
    @Autowired private BranchRepository    branchRepository;
    @Autowired private RouteRepository     routeRepository;
    @Autowired private ProductRepository   productRepository;

    @MockitoSpyBean private StockControlRepository stockControlRepository;

    private Branch    branch;
    private Route     route;
    private Product   product;
    private LocalDate date;

    @BeforeEach
    void setUp() {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        branch  = branchRepository.save(Branch.builder().name("Branch " + suffix).code("B-" + suffix).active(true).build());
        route   = routeRepository.save(Route.builder().code("Rto " + suffix).branch(branch).active(true).build());
        product = productRepository.save(Product.builder().code("P-" + suffix).name("Product " + suffix)
                .displayOrder(1).type(ProductType.RETORNABLE).unit("u").active(true).build());
        date    = LocalDate.now();
    }

    @Test
    void shouldCreateOnlyOneControlWhenTwoRequestsArriveTogether() throws Exception {
        Callable<StockControlResponse> create = () -> stockControlService.createControl(request());

        var outcomes = runConcurrently(List.of(create, create));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(o -> !o.succeeded())
                .singleElement()
                .extracting(Outcome::error)
                .isInstanceOf(DuplicateControlException.class);
        assertThat(activeControls()).isEqualTo(1);
    }

    @Test
    void shouldReturnConflictWhenDuplicateSlipsPastTheExistsCheck() {
        stockControlService.createControl(request());
        // Simulates the race window: the check sees nothing, so only the unique index can stop the insert.
        doReturn(false).when(stockControlRepository)
                .existsByTypeAndRouteIdAndControlDateAndStatusNot(any(), any(), any(), any());

        assertThatThrownBy(() -> stockControlService.createControl(request()))
                .isInstanceOf(DuplicateControlException.class);
        assertThat(activeControls()).isEqualTo(1);
    }

    @Test
    void shouldRejectMovingAControlOntoADateThatAlreadyHasOne() {
        stockControlService.createControl(request());
        var other = stockControlService.createControl(request(date.plusDays(1)));

        assertThatThrownBy(() -> stockControlService.updateControl(other.getId(), moveTo(date)))
                .isInstanceOf(DuplicateControlException.class);
    }

    @Test
    void shouldRejectMovingAControlOntoATakenDateEvenIfTheCheckIsBypassed() {
        stockControlService.createControl(request());
        var other = stockControlService.createControl(request(date.plusDays(1)));
        doReturn(false).when(stockControlRepository)
                .existsByTypeAndRouteIdAndControlDateAndStatusNotAndIdNot(any(), any(), any(), any(), any());

        assertThatThrownBy(() -> stockControlService.updateControl(other.getId(), moveTo(date)))
                .isInstanceOf(DuplicateControlException.class);
    }

    @Test
    void shouldAllowUpdatingAControlWithoutChangingItsDate() {
        var control = stockControlService.createControl(request());

        var updated = stockControlService.updateControl(control.getId(), moveTo(date));

        assertThat(updated.getControlDate()).isEqualTo(date);
    }

    private static UpdateStockControlRequest moveTo(LocalDate newDate) {
        var update = new UpdateStockControlRequest();
        update.setControlDate(newDate);
        return update;
    }

    private long activeControls() {
        return stockControlRepository.findAll().stream()
                .filter(c -> c.getRoute().getId().equals(route.getId()))
                .filter(c -> c.getStatus() != ControlStatus.CANCELLED)
                .count();
    }

    private CreateStockControlRequest request() {
        return request(date);
    }

    private CreateStockControlRequest request(LocalDate controlDate) {
        var item = new CreateStockControlItemRequest();
        item.setProductId(product.getId());
        item.setTotalQuantity(10);
        item.setFullQuantity(10);
        item.setExchangeQuantity(0);

        var request = new CreateStockControlRequest();
        request.setType(ControlType.EXIT);
        request.setBranchId(branch.getId());
        request.setRouteId(route.getId());
        request.setControlDate(controlDate);
        request.setItems(List.of(item));
        return request;
    }
}

package com.loop.new_loop_api.loadforecast.repository;

import com.loop.new_loop_api.branches.entity.Branch;
import com.loop.new_loop_api.branches.repository.BranchRepository;
import com.loop.new_loop_api.products.entity.Product;
import com.loop.new_loop_api.products.entity.ProductType;
import com.loop.new_loop_api.products.repository.ProductRepository;
import com.loop.new_loop_api.routes.entity.Route;
import com.loop.new_loop_api.routes.repository.RouteRepository;
import com.loop.new_loop_api.stockcontrols.entity.ControlStatus;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;
import com.loop.new_loop_api.stockcontrols.entity.StockControl;
import com.loop.new_loop_api.stockcontrols.entity.StockControlItem;
import com.loop.new_loop_api.stockcontrols.repository.StockControlRepository;
import com.loop.new_loop_api.support.PostgresIntegrationTest;
import com.loop.new_loop_api.users.entity.Role;
import com.loop.new_loop_api.users.entity.User;
import com.loop.new_loop_api.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The forecast query and the V43 role constraint against a real PostgreSQL (spec 008). */
class LoadForecastRepositoryTest extends PostgresIntegrationTest {

    // Far in the future so no other test or seed data shares these dates.
    private static final LocalDate LOAD_DAY = LocalDate.of(2099, 3, 17);
    private static final List<LocalDate> REFERENCE_DAYS = List.of(
            LOAD_DAY.minusWeeks(1), LOAD_DAY.minusWeeks(2), LOAD_DAY.minusWeeks(3), LOAD_DAY.minusWeeks(4));

    @Autowired private StockControlRepository stockControlRepository;
    @Autowired private BranchRepository       branchRepository;
    @Autowired private RouteRepository        routeRepository;
    @Autowired private ProductRepository      productRepository;
    @Autowired private UserRepository         userRepository;

    private String  suffix;
    private Branch  branch;
    private Route   route;
    private Route   otherRoute;
    private Product product;

    @BeforeEach
    void setUp() {
        suffix     = UUID.randomUUID().toString().substring(0, 8);
        branch     = branchRepository.save(Branch.builder().name("Branch " + suffix).code("B-" + suffix).active(true).build());
        route      = routeRepository.save(Route.builder().code("Rto F" + suffix).branch(branch).active(true).build());
        otherRoute = routeRepository.save(Route.builder().code("Rto G" + suffix).branch(branch).active(true).build());
        product    = productRepository.save(Product.builder().code("P-" + suffix).name("Product " + suffix)
                .displayOrder(1).type(ProductType.RETORNABLE).unit("u").active(true).build());
    }

    private StockControl save(Route onRoute, ControlType type, LocalDate date, ControlStatus status, int quantity) {
        var control = StockControl.builder().type(type).branch(branch).route(onRoute).controlDate(date).status(status).build();
        control.getItems().add(StockControlItem.builder().stockControl(control).product(product)
                .totalQuantity(quantity).fullQuantity(quantity).exchangeQuantity(0).build());
        return stockControlRepository.saveAndFlush(control);
    }

    @Test
    void shouldReturnOnlyTheRouteNonCancelledExitControlsOnTheReferenceDays_withItemsAndProductsLoaded() {
        var expected1 = save(route, ControlType.EXIT, REFERENCE_DAYS.get(0), ControlStatus.SENT_TO_AGUAS, 100);
        var expected2 = save(route, ControlType.EXIT, REFERENCE_DAYS.get(2), ControlStatus.AGUAS_ERROR, 90);
        save(route, ControlType.EXIT, REFERENCE_DAYS.get(1), ControlStatus.CANCELLED, 999);   // cancelled
        save(route, ControlType.ENTRY, REFERENCE_DAYS.get(3), ControlStatus.ACCEPTED_BY_DRIVER, 999);  // entry
        save(route, ControlType.EXIT, LOAD_DAY.minusDays(1), ControlStatus.SENT_TO_AGUAS, 999);  // not a reference day
        save(otherRoute, ControlType.EXIT, REFERENCE_DAYS.get(0), ControlStatus.SENT_TO_AGUAS, 999);  // other route

        var found = stockControlRepository.findReferenceControls(
                route.getId(), ControlType.EXIT, REFERENCE_DAYS, ControlStatus.CANCELLED);

        assertThat(found).extracting(StockControl::getId).containsExactlyInAnyOrder(expected1.getId(), expected2.getId());
        // Read outside the repository transaction (open-in-view is off): fails if items or products were not fetched.
        assertThat(found).allSatisfy(control -> assertThat(control.getItems())
                .singleElement()
                .satisfies(item -> assertThat(item.getProduct().getCode()).isEqualTo("P-" + suffix)));
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"VISUALIZADOR", "CLARKISTA"})
    void shouldAcceptTheNewRolesInTheUsersTable(Role role) {
        var user = userRepository.saveAndFlush(User.builder()
                .name("Test " + role).username(role.name().toLowerCase() + "-" + suffix).password("x")
                .role(role).branch(branch).build());

        assertThat(userRepository.findById(user.getId())).get().extracting(User::getRole).isEqualTo(role);
    }
}

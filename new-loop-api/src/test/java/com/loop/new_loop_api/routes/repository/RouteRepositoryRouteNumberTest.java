package com.loop.new_loop_api.routes.repository;

import com.loop.new_loop_api.branches.entity.Branch;
import com.loop.new_loop_api.branches.repository.BranchRepository;
import com.loop.new_loop_api.routes.entity.Route;
import com.loop.new_loop_api.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/** The native query runs on every dispenser movement create, so a SQL mistake would fail them all. */
class RouteRepositoryRouteNumberTest extends PostgresIntegrationTest {

    @Autowired private RouteRepository  routeRepository;
    @Autowired private BranchRepository branchRepository;

    private Branch branch;
    // Random numbers: the test database is shared across runs and route codes are unique.
    private long   number;

    @BeforeEach
    void setUp() {
        var suffix = Long.toString(System.nanoTime());
        branch = branchRepository.save(Branch.builder().name("Branch " + suffix).code("B-" + suffix).active(true).build());
        number = ThreadLocalRandom.current().nextLong(100_000_000L, 999_999_999L);
    }

    @Test
    void shouldFindRouteByNumberIgnoringLetters() {
        var route = saveRoute("Rto " + number);

        assertThat(routeRepository.findByRouteNumber(number)).extracting(Route::getId).containsExactly(route.getId());
    }

    @Test
    void shouldReturnEveryRouteSharingTheNumber() {
        saveRoute("Rto " + number);
        saveRoute("R-0" + number);

        assertThat(routeRepository.findByRouteNumber(number)).hasSize(2);
    }

    @Test
    void shouldNotFailOnCodesWithoutDigitsOrWithTooMany() {
        saveRoute("Rto sin numero " + lettersOnly(System.nanoTime()));
        saveRoute("R" + "9".repeat(25) + System.nanoTime());

        assertThat(routeRepository.findByRouteNumber(number)).isEmpty();
    }

    /** A unique suffix with no digits, so the code really has no route number. */
    private static String lettersOnly(long value) {
        var letters = new StringBuilder();
        for (char digit : Long.toString(value).toCharArray()) {
            letters.append((char) ('a' + (digit - '0')));
        }
        return letters.toString();
    }

    private Route saveRoute(String code) {
        return routeRepository.save(Route.builder().code(code).branch(branch).active(true).build());
    }
}

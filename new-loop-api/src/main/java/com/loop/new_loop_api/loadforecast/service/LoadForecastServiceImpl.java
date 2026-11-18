package com.loop.new_loop_api.loadforecast.service;

import com.loop.new_loop_api.common.exception.ForbiddenException;
import com.loop.new_loop_api.common.security.AuthenticatedUser;
import com.loop.new_loop_api.common.security.CurrentUserProvider;
import com.loop.new_loop_api.loadforecast.domain.LoadDay;
import com.loop.new_loop_api.loadforecast.domain.LoadForecastCalculator;
import com.loop.new_loop_api.loadforecast.domain.LoadedProduct;
import com.loop.new_loop_api.loadforecast.domain.ReferenceLoad;
import com.loop.new_loop_api.loadforecast.dto.LoadForecastItemResponse;
import com.loop.new_loop_api.loadforecast.dto.LoadForecastResponse;
import com.loop.new_loop_api.loadforecast.exception.ForecastRouteNotFoundException;
import com.loop.new_loop_api.loadforecast.service.iService.LoadForecastService;
import com.loop.new_loop_api.routes.entity.Route;
import com.loop.new_loop_api.routes.repository.RouteRepository;
import com.loop.new_loop_api.stockcontrols.entity.ControlStatus;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;
import com.loop.new_loop_api.stockcontrols.entity.StockControl;
import com.loop.new_loop_api.stockcontrols.repository.StockControlRepository;
import com.loop.new_loop_api.users.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LoadForecastServiceImpl implements LoadForecastService {

    private static final Set<Role> ALLOWED_ROLES = Set.of(Role.CLARKISTA, Role.ADMIN);

    private final RouteRepository        routeRepository;
    private final StockControlRepository stockControlRepository;
    private final CurrentUserProvider    currentUserProvider;
    private final Clock                  clock;

    @Override
    @Transactional(readOnly = true)
    public LoadForecastResponse forecast(long routeNumber) {
        var user    = currentUserProvider.current()
                .filter(u -> ALLOWED_ROLES.contains(u.role()))
                .orElseThrow(() -> new ForbiddenException("Only forklift operators and admins can read the load forecast"));
        var route   = resolveRoute(user, routeNumber);
        var loadDay = LoadDay.after(LocalDate.now(clock));

        var controls = stockControlRepository.findReferenceControls(
                route.getId(), ControlType.EXIT, LoadDay.referenceDays(loadDay), ControlStatus.CANCELLED);
        var loads = controls.stream().map(LoadForecastServiceImpl::toReferenceLoad).toList();

        var basedOn = loads.stream().map(ReferenceLoad::date).sorted(Comparator.naturalOrder()).toList();
        var items   = LoadForecastCalculator.forecast(loads).stream()
                .map(item -> new LoadForecastItemResponse(item.productCode(), item.productName(), item.quantity()))
                .toList();
        return new LoadForecastResponse(route.getCode(), loadDay, basedOn, items);
    }

    /**
     * A forklift operator only sees routes of their branch; an admin any branch. If the number matches
     * more than one route we refuse rather than guess, since the wrong truck would be loaded.
     */
    private Route resolveRoute(AuthenticatedUser user, long routeNumber) {
        var candidates = routeRepository.findByRouteNumber(routeNumber).stream()
                .filter(route -> Boolean.TRUE.equals(route.getActive()))
                .filter(route -> user.role() == Role.ADMIN
                        || (route.getBranch() != null && Objects.equals(route.getBranch().getId(), user.branchId())))
                .toList();
        if (candidates.size() != 1) {
            throw new ForecastRouteNotFoundException(routeNumber);
        }
        return candidates.getFirst();
    }

    private static ReferenceLoad toReferenceLoad(StockControl control) {
        List<LoadedProduct> products = control.getItems().stream()
                .map(item -> new LoadedProduct(
                        item.getProduct().getId(),
                        item.getProduct().getCode(),
                        item.getProduct().getName(),
                        item.getProduct().getDisplayOrder(),
                        item.getTotalQuantity() == null ? 0 : item.getTotalQuantity()))
                .toList();
        return new ReferenceLoad(control.getControlDate(), products);
    }
}

package com.loop.new_loop_api.routes.service;

import com.loop.new_loop_api.audit.service.iService.AuditService;
import com.loop.new_loop_api.branches.repository.BranchRepository;
import com.loop.new_loop_api.common.service.ActivationService;
import com.loop.new_loop_api.routes.dto.RouteResponse;
import com.loop.new_loop_api.routes.entity.Route;
import com.loop.new_loop_api.routes.mapper.RouteMapper;
import com.loop.new_loop_api.routes.repository.RouteRepository;
import com.loop.new_loop_api.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteServiceImplTest {

    @Mock private RouteRepository   routeRepository;
    @Mock private RouteMapper       routeMapper;
    @Mock private BranchRepository  branchRepository;
    @Mock private UserRepository    userRepository;
    @Mock private AuditService      auditService;
    @Mock private ActivationService activationService;

    @InjectMocks private RouteServiceImpl routeService;

    @Test
    void getAllRoutes_trimsSearch_dropsSort_andMapsResults() {
        var route    = new Route();
        var response = RouteResponse.builder().id(UUID.randomUUID()).code("Rto 2").build();
        Page<Route> repoPage = new PageImpl<>(List.of(route));

        var captor = ArgumentCaptor.forClass(Pageable.class);
        when(routeRepository.searchRoutes(eq("rto"), captor.capture())).thenReturn(repoPage);
        when(routeMapper.toResponse(route)).thenReturn(response);

        var result = routeService.getAllRoutes("  rto  ",
                PageRequest.of(2, 100, Sort.by("code").descending()));

        assertThat(result.getContent()).containsExactly(response);

        var forwarded = captor.getValue();
        assertThat(forwarded.getPageNumber()).isEqualTo(2);
        assertThat(forwarded.getPageSize()).isEqualTo(100);
        assertThat(forwarded.getSort().isSorted())
                .as("Pageable sort must be dropped; ordering lives in the native query")
                .isFalse();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void getAllRoutes_normalizesBlankSearchToNull(String rawSearch) {
        when(routeRepository.searchRoutes(eq(null), any())).thenReturn(new PageImpl<>(List.of()));

        routeService.getAllRoutes(rawSearch, PageRequest.of(0, 20));

        verify(routeRepository).searchRoutes(eq(null), any());
    }
}

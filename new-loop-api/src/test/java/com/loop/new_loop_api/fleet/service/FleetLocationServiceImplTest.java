package com.loop.new_loop_api.fleet.service;

import com.loop.new_loop_api.fleet.dto.TruckLocationResponse;
import com.loop.new_loop_api.fleet.exception.FleetProviderException;
import com.loop.new_loop_api.fleet.mapper.TruckLocationMapper;
import com.loop.new_loop_api.integrations.common.metrics.IntegrationCallMetrics;
import com.loop.new_loop_api.integrations.powerFleet.client.PowerfleetClient;
import com.loop.new_loop_api.integrations.powerFleet.dto.PowerfleetFleetViewResponse;
import com.loop.new_loop_api.integrations.powerFleet.dto.PowerfleetTokenResponse;
import com.loop.new_loop_api.integrations.powerFleet.dto.PowerfleetVehicle;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FleetLocationServiceImplTest {

    private static final String PLATE = "LRO548";

    @Mock private PowerfleetClient       powerfleetClient;
    @Mock private TruckLocationMapper    truckLocationMapper;
    @Mock private IntegrationCallMetrics integrationCallMetrics;

    @InjectMocks private FleetLocationServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "username", "user");
        ReflectionTestUtils.setField(service, "password", "pass");
    }

    @Test
    void shouldRenewTokenAndRetryOnce_whenFleetViewRejectsCachedToken() {
        when(powerfleetClient.getToken(any())).thenReturn(token("stale"), token("fresh"));
        var vehicle = vehicle();
        var expected = mock(TruckLocationResponse.class);
        when(powerfleetClient.getFleetView("Bearer stale")).thenThrow(unauthorized());
        when(powerfleetClient.getFleetView("Bearer fresh")).thenReturn(fleetView(vehicle));
        when(truckLocationMapper.toResponse(vehicle)).thenReturn(expected);

        assertThat(service.getLocation(PLATE)).isSameAs(expected);
        verify(powerfleetClient, times(2)).getToken(any());
    }

    @Test
    void shouldExposePowerfleetMessage_whenTokenRequestIsRejected() {
        var rejected = new PowerfleetTokenResponse();
        rejected.setIsSucceded(false);
        var error = new PowerfleetTokenResponse.Error();
        error.setMessage("Invalid username or password");
        rejected.setError(error);
        when(powerfleetClient.getToken(any())).thenReturn(rejected);

        assertThatThrownBy(() -> service.getLocation(PLATE))
                .isInstanceOf(FleetProviderException.class)
                .rootCause()
                .hasMessageContaining("Invalid username or password");
    }

    private PowerfleetTokenResponse token(String value) {
        var response = new PowerfleetTokenResponse();
        response.setToken(value);
        response.setExpire(OffsetDateTime.now().plusHours(1).toString());
        return response;
    }

    private PowerfleetVehicle vehicle() {
        var vehicle = new PowerfleetVehicle();
        vehicle.setLicensePlate(PLATE);
        return vehicle;
    }

    private PowerfleetFleetViewResponse fleetView(PowerfleetVehicle vehicle) {
        var response = new PowerfleetFleetViewResponse();
        response.setData(List.of(vehicle));
        return response;
    }

    private FeignException unauthorized() {
        var request = Request.create(Request.HttpMethod.GET, "/api/fleetview", Map.of(), null, StandardCharsets.UTF_8, null);
        return new FeignException.Unauthorized("401 Unauthorized", request, null, Map.of());
    }
}

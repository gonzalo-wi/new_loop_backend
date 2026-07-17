package com.loop.new_loop_api.fleet.service;

import com.loop.new_loop_api.fleet.dto.TruckLocationResponse;
import com.loop.new_loop_api.fleet.exception.FleetProviderException;
import com.loop.new_loop_api.fleet.exception.TruckLocationNotFoundException;
import com.loop.new_loop_api.fleet.mapper.TruckLocationMapper;
import com.loop.new_loop_api.fleet.service.iService.FleetLocationService;
import com.loop.new_loop_api.integrations.powerFleet.client.PowerfleetClient;
import com.loop.new_loop_api.integrations.powerFleet.dto.PowerfleetTokenRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class FleetLocationServiceImpl implements FleetLocationService {

    private static final Logger log = LoggerFactory.getLogger(FleetLocationServiceImpl.class);
    private static final int    TOKEN_BUFFER_MINUTES = 5;

    private final PowerfleetClient    powerfleetClient;
    private final TruckLocationMapper truckLocationMapper;

    @Value("${integrations.powerfleet.username}")
    private String username;

    @Value("${integrations.powerfleet.password}")
    private String password;

    private volatile String         cachedToken;
    private volatile OffsetDateTime tokenExpiry;

    @Override
    public TruckLocationResponse getLocation(String licensePlate) {
        try {
            var fleetView = powerfleetClient.getFleetView("Bearer " + validToken());
            var vehicle = fleetView.getData() == null ? null : fleetView.getData().stream()
                    .filter(v -> licensePlate.equalsIgnoreCase(v.getLicensePlate()))
                    .findFirst()
                    .orElse(null);

            if (vehicle == null) {
                throw new TruckLocationNotFoundException(licensePlate);
            }
            return truckLocationMapper.toResponse(vehicle);
        } catch (TruckLocationNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new FleetProviderException("Could not retrieve truck location from Powerfleet", e);
        }
    }

    /** Token cache: reused until close to expiry, refreshed under lock otherwise. */
    private synchronized String validToken() {
        if (cachedToken != null && tokenExpiry != null
                && OffsetDateTime.now().isBefore(tokenExpiry.minusMinutes(TOKEN_BUFFER_MINUTES))) {
            return cachedToken;
        }
        log.info("Requesting new Powerfleet token");
        var request = PowerfleetTokenRequest.builder()
                .username(username)
                .password(password)
                .langId(1)
                .build();
        var response = powerfleetClient.getToken(request);
        if (response == null || response.getToken() == null) {
            throw new FleetProviderException("Powerfleet did not return a valid token", null);
        }
        cachedToken = response.getToken();
        tokenExpiry = OffsetDateTime.parse(response.getExpire());
        log.info("Powerfleet token renewed, expires at {}", tokenExpiry);
        return cachedToken;
    }
}

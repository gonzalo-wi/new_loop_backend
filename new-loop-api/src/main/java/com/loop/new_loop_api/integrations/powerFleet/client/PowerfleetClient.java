package com.loop.new_loop_api.integrations.powerFleet.client;

import com.loop.new_loop_api.integrations.powerFleet.dto.PowerfleetFleetViewResponse;
import com.loop.new_loop_api.integrations.powerFleet.dto.PowerfleetTokenRequest;
import com.loop.new_loop_api.integrations.powerFleet.dto.PowerfleetTokenResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "powerfleetClient", url = "${integrations.powerfleet.base-url}")
public interface PowerfleetClient {

    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_JSON_VALUE)
    PowerfleetTokenResponse getToken(@RequestBody PowerfleetTokenRequest request);

    @GetMapping("/api/fleetview")
    PowerfleetFleetViewResponse getFleetView(@RequestHeader("Authorization") String bearerToken);
}

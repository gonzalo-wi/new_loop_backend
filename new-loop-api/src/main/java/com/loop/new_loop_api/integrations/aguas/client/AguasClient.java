package com.loop.new_loop_api.integrations.aguas.client;

import com.loop.new_loop_api.integrations.aguas.dto.AguasEntryRequest;
import com.loop.new_loop_api.integrations.aguas.dto.AguasExitRequest;
import feign.Response;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "aguasClient", url = "${integrations.aguas.base-url}")
public interface AguasClient {

    // Returns the raw Response so we evaluate the HTTP status ourselves and tolerate empty bodies.
    @PostMapping(value = "/api/aguas/in", consumes = MediaType.APPLICATION_JSON_VALUE)
    Response sendEntry(@RequestBody AguasEntryRequest request);

    @PostMapping(value = "/api/aguas/out", consumes = MediaType.APPLICATION_JSON_VALUE)
    Response sendExit(@RequestBody AguasExitRequest request);
}

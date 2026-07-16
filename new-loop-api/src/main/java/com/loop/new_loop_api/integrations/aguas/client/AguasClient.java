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

    @PostMapping(value = "/api/aguas/products/in", consumes = MediaType.APPLICATION_JSON_VALUE)
    Response sendEntry(@RequestBody AguasEntryRequest request);

    @PostMapping(value = "/api/aguas/products/out", consumes = MediaType.APPLICATION_JSON_VALUE)
    Response sendExit(@RequestBody AguasExitRequest request);
}

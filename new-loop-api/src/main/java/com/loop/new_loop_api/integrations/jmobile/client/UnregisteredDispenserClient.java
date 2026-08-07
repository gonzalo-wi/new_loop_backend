package com.loop.new_loop_api.integrations.jmobile.client;

import com.loop.new_loop_api.integrations.jmobile.dto.UnregisteredDispenserListResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "unregisteredDispenserClient", url = "${integrations.jmobile.base-url}")
public interface UnregisteredDispenserClient {

    /**
     * Dispensers flagged as "no registrado" for a given day. The date is part of the path
     * (jMobile style: getDispenserNoRegistrado=fecha=2026-08-07), not a query parameter.
     */
    @GetMapping("/jmobile/service/dispenserope/getDispenserNoRegistrado=fecha={fecha}")
    UnregisteredDispenserListResponse getUnregisteredDispensers(@PathVariable("fecha") String fecha);
}

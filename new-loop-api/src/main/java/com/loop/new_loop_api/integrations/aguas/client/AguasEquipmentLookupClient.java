package com.loop.new_loop_api.integrations.aguas.client;

import com.loop.new_loop_api.integrations.aguas.dto.AguasEquipmentLookupResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Read-only lookup of a cold/hot dispenser by serial in Aguas. Its own client (not {@link AguasEquipmentClient})
 * so it gets short timeouts: it runs while the operator scans and when a load is registered.
 */
@FeignClient(name = "aguasEquipmentLookupClient", url = "${integrations.aguas-equipment.base-url}")
public interface AguasEquipmentLookupClient {

    /** Exact match; a serial ending in '*' is a different equipment than the same serial without it. */
    @GetMapping("/api/aguas/equipo-fc")
    AguasEquipmentLookupResponse findBySerial(@RequestParam("nroserie") String nroserie);
}

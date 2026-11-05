package com.loop.new_loop_api.integrations.pac.client;

import com.loop.new_loop_api.integrations.pac.dto.PacSettlementResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

/** PAC's per-route settlement (PAC spec 015). The Basic header is sent on every call, without a challenge. */
@FeignClient(name = "pacSettlementClient", url = "${integrations.pac.base-url}")
public interface PacSettlementClient {

    @GetMapping("/api/integrations/loop/settlement")
    PacSettlementResponse getSettlement(@RequestHeader("Authorization") String basicAuthorization,
                                        @RequestParam("routeNumber") long routeNumber,
                                        @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date);
}

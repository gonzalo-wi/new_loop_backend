package com.loop.new_loop_api.integrations.jmobile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loop.new_loop_api.integrations.jmobile.client.UnregisteredDispenserClient;
import feign.Feign;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.support.SpringMvcContract;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * jMobile takes the date as part of the path (getDispenserNoRegistrado=fecha=...), so this checks
 * that Feign keeps the literal '=' unencoded — with '%3D' the service answers 404.
 */
class UnregisteredDispenserClientTest {

    private static final String BODY = """
            {"success":true,"listado":[
              {"fecha":"2026-08-07 14:53:00.0","nroSerie":"1518208243888","nroReparto":199,"nroCta":"1171320","id":1},
              {"fecha":"2026-08-07 14:53:53.0","nroSerie":"LM29H01181809","nroReparto":199,"nroCta":"1171320","id":2}
            ]}""";

    @Test
    void sendsTheDateInThePathAndParsesTheList() {
        var requestedUrl = new AtomicReference<String>();
        var objectMapper = new ObjectMapper();

        UnregisteredDispenserClient client = Feign.builder()
                .contract(new SpringMvcContract())
                .decoder((response, type) -> objectMapper.readValue(
                        response.body().asInputStream(), objectMapper.constructType(type)))
                .client((request, options) -> {
                    requestedUrl.set(request.url());
                    return Response.builder()
                            .status(200)
                            .request(request)
                            .body(BODY, StandardCharsets.UTF_8)
                            .build();
                })
                .target(UnregisteredDispenserClient.class, "http://localhost:8080");

        var response = client.getUnregisteredDispensers("2026-08-07");

        assertEquals("http://localhost:8080/jmobile/service/dispenserope/getDispenserNoRegistrado=fecha=2026-08-07",
                requestedUrl.get());
        assertTrue(response.isSuccess());
        assertEquals(2, response.getListado().size());
        assertEquals("LM29H01181809", response.getListado().get(1).getNroSerie());
    }
}

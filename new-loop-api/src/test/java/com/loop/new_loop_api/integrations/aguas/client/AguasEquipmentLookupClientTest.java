package com.loop.new_loop_api.integrations.aguas.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Feign;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.support.SpringMvcContract;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AguasEquipmentLookupClientTest {

    private static final String FOUND = """
            {"success":true,"data":{"existe":true,"nro_serie":"LM29P05304778*","equipo":{
              "id_equipo":13254,"nro_serie":"LM29P05304778*","id_producto":"U","id_marca":22,"marca":"LAMO",
              "id_modelo":66,"modelo":"LM29P","id_ubicacion":3,"ubicacion":"EN CAMIONETA","id_estado":5,"estado":"B",
              "fecha_alta":"2017-07-22 00:00:00.000","fecha_modificacion":"2017-07-22 00:00:00.000"}}}""";

    private static final String MISSING = """
            {"success":true,"data":{"existe":false,"nro_serie":"LM29P99999999","equipo":null}}""";

    @Test
    void shouldSendTheSerialAsEncodedQueryParamAndParseAFoundEquipment() {
        var requestedUrl = new AtomicReference<String>();
        var client = clientAnswering(FOUND, requestedUrl);

        var response = client.findBySerial("LM29P05304778*");

        assertThat(requestedUrl.get())
                .isEqualTo("http://localhost/api/aguas/equipo-fc?nroserie=LM29P05304778%2A");
        assertThat(response.success()).isTrue();
        assertThat(response.data().existe()).isTrue();
        assertThat(response.data().nroSerie()).isEqualTo("LM29P05304778*");
        assertThat(response.data().equipo().ubicacion()).isEqualTo("EN CAMIONETA");
        assertThat(response.data().equipo().estado()).isEqualTo("B");
    }

    @Test
    void shouldParseAMissingEquipmentWithNullEquipo() {
        var response = clientAnswering(MISSING, new AtomicReference<>()).findBySerial("LM29P99999999");

        assertThat(response.data().existe()).isFalse();
        assertThat(response.data().equipo()).isNull();
    }

    private static AguasEquipmentLookupClient clientAnswering(String body, AtomicReference<String> requestedUrl) {
        var objectMapper = new ObjectMapper();
        return Feign.builder()
                .contract(new SpringMvcContract())
                .decoder((response, type) -> objectMapper.readValue(
                        response.body().asInputStream(), objectMapper.constructType(type)))
                .client((request, options) -> {
                    requestedUrl.set(request.url());
                    return Response.builder().status(200).request(request)
                            .body(body, StandardCharsets.UTF_8).build();
                })
                .target(AguasEquipmentLookupClient.class, "http://localhost");
    }
}

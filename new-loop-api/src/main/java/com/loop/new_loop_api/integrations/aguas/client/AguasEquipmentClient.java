package com.loop.new_loop_api.integrations.aguas.client;

import com.loop.new_loop_api.integrations.aguas.dto.AguasDeleteEquipmentRequest;
import com.loop.new_loop_api.integrations.aguas.dto.AguasEquipmentMovementRequest;
import feign.Response;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "aguasEquipmentClient", url = "${integrations.aguas-equipment.base-url}")
public interface AguasEquipmentClient {

    // Carga de dispensers al camión
    @PostMapping(value = "/api/aguas/registrar-salida-camion", consumes = MediaType.APPLICATION_JSON_VALUE)
    Response registerTruckDeparture(@RequestBody AguasEquipmentMovementRequest request);

    // Descarga / vuelta del camión
    @PostMapping(value = "/api/aguas/registrar-vuelta-camion", consumes = MediaType.APPLICATION_JSON_VALUE)
    Response registerTruckReturn(@RequestBody AguasEquipmentMovementRequest request);

    // Eliminar un movimiento previamente registrado
    @DeleteMapping(value = "/api/aguas/movimiento-equipo", consumes = MediaType.APPLICATION_JSON_VALUE)
    Response deleteMovement(@RequestBody AguasDeleteEquipmentRequest request);

    // Catálogos para poblar los selectores del frontend
    @GetMapping("/api/aguas/ubicaciones-destino")
    Response getDestinationLocations();

    @GetMapping("/api/aguas/estados-destino")
    Response getDestinationStates();
}

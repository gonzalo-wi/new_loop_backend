package com.loop.new_loop_api.integrations.odoo.mapper;

import com.loop.new_loop_api.dispensers.entity.DispenserMovement;
import com.loop.new_loop_api.integrations.odoo.dto.OdooDispatchAvailableParams;
import com.loop.new_loop_api.integrations.odoo.dto.OdooDispatchAvailableRequest;
import com.loop.new_loop_api.integrations.odoo.dto.OdooDispatchCreateParams;
import com.loop.new_loop_api.integrations.odoo.dto.OdooDispatchCreateRequest;
import com.loop.new_loop_api.integrations.odoo.dto.OdooDispatchValidateParams;
import com.loop.new_loop_api.integrations.odoo.dto.OdooDispatchValidateRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class OdooDispatchMapper {

    private static final String EXTERNAL_REFERENCE_PREFIX = "LOOP";
    private static final int    UUID_SUFFIX_LENGTH         = 8;

    public OdooDispatchCreateRequest toCreateRequest(DispenserMovement movement, String referenciaExterna) {
        var params = OdooDispatchCreateParams.builder()
                .fecha(movement.getMovementDate().toString().replace("-", ""))
                .idreparto(movement.getRouteCode())
                .equipos(new ArrayList<>(movement.serialsToSend()))
                .referenciaExterna(referenciaExterna)
                .build();

        return OdooDispatchCreateRequest.builder()
                .params(params)
                .build();
    }

    public OdooDispatchValidateRequest toValidateRequest(List<String> equipos) {
        var params = OdooDispatchValidateParams.builder()
                .equipos(equipos)
                .build();

        return OdooDispatchValidateRequest.builder()
                .params(params)
                .build();
    }

    public OdooDispatchAvailableRequest toAvailableRequest(Integer limite, Integer offset) {
        var params = OdooDispatchAvailableParams.builder()
                .limite(limite)
                .offset(offset)
                .build();

        return OdooDispatchAvailableRequest.builder()
                .params(params)
                .build();
    }

    /**
     * Deterministic external reference for /salida/create: same movement id always yields the
     * same reference, so a retry (before or after a network cut) reuses it and Odoo can dedupe
     * instead of moving stock twice.
     */
    public String buildExternalReference(DispenserMovement movement) {
        var datePart   = movement.getMovementDate().toString().replace("-", "");
        var uuidSuffix = movement.getId().toString().replace("-", "")
                .substring(0, UUID_SUFFIX_LENGTH).toUpperCase();
        return String.join("-", EXTERNAL_REFERENCE_PREFIX, datePart, movement.getRouteCode(), uuidSuffix);
    }
}

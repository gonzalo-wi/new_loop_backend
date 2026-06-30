package com.loop.new_loop_api.integrations.aguas.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AguasExitRequest {

    private List<AguasExitProduct> products;

    @JsonProperty("delivery_id")
    private Integer deliveryId;

    private String date;

    private AguasSucursal sucursal;

    private String comments;
}

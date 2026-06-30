package com.loop.new_loop_api.integrations.aguas.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AguasEntryRequest {

    private List<AguasEntryProduct> products;

    @JsonProperty("delivery_id")
    private Integer deliveryId;

    private String supervisor;
    private String comments;
}

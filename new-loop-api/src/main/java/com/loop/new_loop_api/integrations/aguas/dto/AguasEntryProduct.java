package com.loop.new_loop_api.integrations.aguas.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AguasEntryProduct {

    @JsonProperty("product_id")
    private String productId;

    private Integer total;
    private Integer filled;
    private Integer recharge;
}

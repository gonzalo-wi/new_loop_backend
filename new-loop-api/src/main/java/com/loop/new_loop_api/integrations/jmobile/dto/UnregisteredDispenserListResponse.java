package com.loop.new_loop_api.integrations.jmobile.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Response of jMobile GET getDispenserNoRegistrado=fecha={fecha}. */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UnregisteredDispenserListResponse {

    private boolean                    success;
    private List<UnregisteredDispenser> listado;
}

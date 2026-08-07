package com.loop.new_loop_api.integrations.jmobile.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One entry of the jMobile "dispenser no registrado" list. */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UnregisteredDispenser {

    private Long    id;
    private String  fecha;
    private String  nroSerie;
    private Integer nroReparto;
    private String  nroCta;
}

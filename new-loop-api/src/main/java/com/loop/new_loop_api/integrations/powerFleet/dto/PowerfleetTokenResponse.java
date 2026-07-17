package com.loop.new_loop_api.integrations.powerFleet.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PowerfleetTokenResponse {
    private String token;
    private String expire;
}

package com.loop.new_loop_api.integrations.powerFleet.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PowerfleetTokenRequest {
    private String username;
    private String password;
    private int    langId;
}

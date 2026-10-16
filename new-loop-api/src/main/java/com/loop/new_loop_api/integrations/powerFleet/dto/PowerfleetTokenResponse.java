package com.loop.new_loop_api.integrations.powerFleet.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PowerfleetTokenResponse {
    private String  token;
    private String  expire;
    private Boolean isSucceded;
    private Error   error;

    /** Powerfleet answers a rejected login with 200/400 and {"isSucceded":false,"error":{...}}. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Error {
        private Integer code;
        private String  message;
    }
}

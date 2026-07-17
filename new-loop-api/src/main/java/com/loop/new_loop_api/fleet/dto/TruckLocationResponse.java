package com.loop.new_loop_api.fleet.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TruckLocationResponse {
    private String  licensePlate;
    private Double  lat;
    private Double  lng;
    private String  address;
    private Integer speed;
    private Boolean engineOn;
    private String  stateIcon;
    private String  driver;
    private String  gpsDateTime;
    private Integer direction;
}

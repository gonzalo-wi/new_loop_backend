package com.loop.new_loop_api.integrations.powerFleet.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PowerfleetVehicle {
    private Integer id;
    private Long    unitId;
    private Integer engineOn;
    private Double  lat;
    private Double  lng;
    private Integer direction;
    private String  licensePlate;
    private String  driver;
    private String  gpsDateTime;
    private Integer speed;
    private String  stateIcon;
    private String  address;
    private String  lastMove;
}

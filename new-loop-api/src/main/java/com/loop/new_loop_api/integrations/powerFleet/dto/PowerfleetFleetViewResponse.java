package com.loop.new_loop_api.integrations.powerFleet.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class PowerfleetFleetViewResponse {
    private List<PowerfleetVehicle> data;
}

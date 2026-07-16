package com.loop.new_loop_api.branches.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class BranchResponse {

    private UUID          id;
    private String        name;
    private String        code;
    private String        address;
    private String        locality;
    private String        province;
    private String        cuit;
    private String        vatCondition;
    private Boolean       active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.loop.new_loop_api.users.dto;

import com.loop.new_loop_api.users.entity.Role;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class UserResponse {

    private UUID          id;
    private String        name;
    private String        username;
    private Role          role;
    private UUID          branchId;
    private String        branchName;
    private Boolean       active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

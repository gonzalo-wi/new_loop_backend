package com.loop.new_loop_api.auth.dto;

import com.loop.new_loop_api.users.entity.Role;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class LoginResponse {

    private String token;
    private String type;
    private UUID   id;
    private String name;
    private String username;
    private Role   role;
}

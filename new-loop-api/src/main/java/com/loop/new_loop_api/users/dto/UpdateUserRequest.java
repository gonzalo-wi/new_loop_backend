package com.loop.new_loop_api.users.dto;

import com.loop.new_loop_api.users.entity.Role;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class UpdateUserRequest {

    @Size(max = 150, message = "Name must not exceed 150 characters")
    private String name;

    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    private Role role;
}

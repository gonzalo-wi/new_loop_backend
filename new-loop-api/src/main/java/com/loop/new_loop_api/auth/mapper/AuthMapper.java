package com.loop.new_loop_api.auth.mapper;

import com.loop.new_loop_api.auth.dto.LoginResponse;
import com.loop.new_loop_api.users.entity.User;
import org.springframework.stereotype.Component;

@Component
public class AuthMapper {

    public LoginResponse toLoginResponse(User user, String token) {
        return LoginResponse.builder()
                .token(token)
                .type("Bearer")
                .id(user.getId())
                .name(user.getName())
                .username(user.getUsername())
                .role(user.getRole())
                .build();
    }
}

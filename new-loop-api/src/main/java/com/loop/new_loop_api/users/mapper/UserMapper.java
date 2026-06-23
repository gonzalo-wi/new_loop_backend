package com.loop.new_loop_api.users.mapper;

import com.loop.new_loop_api.users.dto.CreateUserRequest;
import com.loop.new_loop_api.users.dto.UpdateUserRequest;
import com.loop.new_loop_api.users.dto.UserResponse;
import com.loop.new_loop_api.users.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public User toEntity(CreateUserRequest request, String hashedPassword) {
        return User.builder()
                .name(request.getName())
                .username(request.getUsername())
                .password(hashedPassword)
                .role(request.getRole())
                .active(true)
                .build();
    }

    public void updateEntity(UpdateUserRequest request, User user, String hashedPassword) {
        if (request.getName() != null) user.setName(request.getName());
        if (request.getRole() != null) user.setRole(request.getRole());
        if (hashedPassword != null)    user.setPassword(hashedPassword);
    }

    public UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .username(user.getUsername())
                .role(user.getRole())
                .active(user.getActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}

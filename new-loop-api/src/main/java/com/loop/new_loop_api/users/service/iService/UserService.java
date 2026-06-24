package com.loop.new_loop_api.users.service.iService;

import com.loop.new_loop_api.users.dto.CreateUserRequest;
import com.loop.new_loop_api.users.dto.UpdateUserRequest;
import com.loop.new_loop_api.users.dto.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface UserService {

    UserResponse createUser(CreateUserRequest request);
    Page<UserResponse> getAllUsers(Pageable pageable);
    UserResponse getUserById(UUID id);
    UserResponse updateUser(UUID id, UpdateUserRequest request);
    void deactivateUser(UUID id);
    void activateUser(UUID id);
    void updateFcmToken(UUID id, String fcmToken);
}

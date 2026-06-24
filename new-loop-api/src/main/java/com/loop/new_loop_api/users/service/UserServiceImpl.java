package com.loop.new_loop_api.users.service;

import com.loop.new_loop_api.audit.service.iService.AuditService;
import com.loop.new_loop_api.users.dto.CreateUserRequest;
import com.loop.new_loop_api.users.dto.UpdateUserRequest;
import com.loop.new_loop_api.users.dto.UserResponse;
import com.loop.new_loop_api.users.entity.User;
import com.loop.new_loop_api.users.exception.UserNotFoundException;
import com.loop.new_loop_api.users.exception.UsernameAlreadyExistsException;
import com.loop.new_loop_api.users.mapper.UserMapper;
import com.loop.new_loop_api.users.repository.UserRepository;
import com.loop.new_loop_api.users.service.iService.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository  userRepository;
    private final UserMapper      userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService    auditService;

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UsernameAlreadyExistsException(request.getUsername());
        }
        var user     = userMapper.toEntity(request, passwordEncoder.encode(request.getPassword()));
        var response = userMapper.toResponse(userRepository.save(user));
        auditService.register("CREATE_USER", "User", response.getId(), null, response);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(userMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        return userMapper.toResponse(findUserById(id));
    }

    @Override
    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        var user           = findUserById(id);
        var oldValue       = userMapper.toResponse(user);
        var hashedPassword = request.getPassword() != null
                ? passwordEncoder.encode(request.getPassword())
                : null;
        userMapper.updateEntity(request, user, hashedPassword);
        var response = userMapper.toResponse(userRepository.save(user));
        auditService.register("UPDATE_USER", "User", id, oldValue, response);
        return response;
    }

    @Override
    @Transactional
    public void deactivateUser(UUID id) {
        var user = findUserById(id);
        user.setActive(false);
        userRepository.save(user);
        auditService.register("DEACTIVATE_USER", "User", id, null, null);
    }

    @Override
    @Transactional
    public void activateUser(UUID id) {
        var user = findUserById(id);
        user.setActive(true);
        userRepository.save(user);
        auditService.register("ACTIVATE_USER", "User", id, null, null);
    }

    @Override
    @Transactional
    public void updateFcmToken(UUID id, String fcmToken) {
        var user = findUserById(id);
        user.setFcmToken(fcmToken);
        userRepository.save(user);
    }

    private User findUserById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }
}

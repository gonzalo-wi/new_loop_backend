package com.loop.new_loop_api.users.service;

import com.loop.new_loop_api.users.dto.CreateUserRequest;
import com.loop.new_loop_api.users.dto.UpdateUserRequest;
import com.loop.new_loop_api.users.dto.UserResponse;
import com.loop.new_loop_api.users.exception.UserNotFoundException;
import com.loop.new_loop_api.users.exception.UsernameAlreadyExistsException;
import com.loop.new_loop_api.users.mapper.UserMapper;
import com.loop.new_loop_api.users.repository.UserRepository;
import com.loop.new_loop_api.users.service.iService.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService, UserDetailsService {

    private final UserRepository  userRepository;
    private final UserMapper      userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UsernameAlreadyExistsException(request.getUsername());
        }
        var user = userMapper.toEntity(request, passwordEncoder.encode(request.getPassword()));
        return userMapper.toResponse(userRepository.save(user));
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
        return userRepository.findById(id)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    @Override
    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        var user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        var hashedPassword = request.getPassword() != null
                ? passwordEncoder.encode(request.getPassword())
                : null;
        userMapper.updateEntity(request, user, hashedPassword);
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public void deactivateUser(UUID id) {
        var user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        user.setActive(false);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void activateUser(UUID id) {
        var user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        user.setActive(true);
        userRepository.save(user);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        return User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .roles(user.getRole().name())
                .disabled(!user.getActive())
                .build();
    }
}

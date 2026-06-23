package com.loop.new_loop_api.auth.service;

import com.loop.new_loop_api.auth.dto.LoginRequest;
import com.loop.new_loop_api.auth.dto.LoginResponse;
import com.loop.new_loop_api.auth.mapper.AuthMapper;
import com.loop.new_loop_api.auth.service.iService.AuthService;
import com.loop.new_loop_api.common.security.JwtService;
import com.loop.new_loop_api.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService    userDetailsService;
    private final JwtService            jwtService;
    private final UserRepository        userRepository;
    private final AuthMapper            authMapper;

    @Override
    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        var user    = userRepository.findByUsername(request.getUsername()).orElseThrow();
        var details = userDetailsService.loadUserByUsername(request.getUsername());
        var token   = jwtService.generateToken(details, Map.of("role", user.getRole().name()));
        return authMapper.toLoginResponse(user, token);
    }
}

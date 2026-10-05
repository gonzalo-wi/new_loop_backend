package com.loop.new_loop_api.auth.service;

import com.loop.new_loop_api.audit.service.iService.AuditService;
import com.loop.new_loop_api.auth.dto.LoginRequest;
import com.loop.new_loop_api.auth.dto.LoginResponse;
import com.loop.new_loop_api.auth.mapper.AuthMapper;
import com.loop.new_loop_api.auth.service.iService.AuthService;
import com.loop.new_loop_api.common.security.JwtService;
import com.loop.new_loop_api.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
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
    private final AuditService          auditService;

    @Override
    public LoginResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
        } catch (AuthenticationException ex) {
            auditService.registerFailedLogin(request.getUsername(), failureReason(ex));
            throw ex;
        }
        var user    = userRepository.findByUsername(request.getUsername()).orElseThrow();
        var details = userDetailsService.loadUserByUsername(request.getUsername());
        var token   = jwtService.generateToken(details, Map.of("role", user.getRole().name()));
        auditService.registerLogin(user.getId(), user.getUsername(), user.getRole().name());
        return authMapper.toLoginResponse(user, token);
    }

    private static String failureReason(AuthenticationException ex) {
        if (ex instanceof BadCredentialsException) return "BAD_CREDENTIALS";
        if (ex instanceof DisabledException)       return "USER_DISABLED";
        return "AUTHENTICATION_FAILED";
    }
}

package com.loop.new_loop_api.auth.service;

import com.loop.new_loop_api.audit.service.iService.AuditService;
import com.loop.new_loop_api.auth.dto.LoginRequest;
import com.loop.new_loop_api.auth.dto.LoginResponse;
import com.loop.new_loop_api.auth.mapper.AuthMapper;
import com.loop.new_loop_api.auth.service.iService.AuthService;
import com.loop.new_loop_api.common.security.JwtService;
import com.loop.new_loop_api.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

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
            auditFailedLogin(request.getUsername(), ex);
            throw ex;
        }
        var user    = userRepository.findByUsername(request.getUsername()).orElseThrow();
        var details = userDetailsService.loadUserByUsername(request.getUsername());
        var token   = jwtService.generateToken(details, Map.of("role", user.getRole().name()));
        auditService.registerLogin(user.getId(), user.getUsername(), user.getRole().name());
        return authMapper.toLoginResponse(user, token);
    }

    // A failure to audit must not turn a 401 into a 500: it travels as a suppressed exception instead.
    private void auditFailedLogin(String attemptedUsername, AuthenticationException ex) {
        try {
            if (userRepository.existsByUsername(attemptedUsername)) {
                auditService.registerFailedLogin(attemptedUsername, LoginFailureReason.of(ex).name());
            } else {
                auditService.registerFailedLogin(null, LoginFailureReason.UNKNOWN_USER.name());
            }
        } catch (RuntimeException auditError) {
            log.error("Could not audit a failed login", auditError);
            ex.addSuppressed(auditError);
        }
    }
}

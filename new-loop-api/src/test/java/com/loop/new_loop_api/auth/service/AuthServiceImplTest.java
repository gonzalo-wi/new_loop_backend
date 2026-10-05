package com.loop.new_loop_api.auth.service;

import com.loop.new_loop_api.audit.service.iService.AuditService;
import com.loop.new_loop_api.auth.dto.LoginRequest;
import com.loop.new_loop_api.auth.mapper.AuthMapper;
import com.loop.new_loop_api.common.security.JwtService;
import com.loop.new_loop_api.users.entity.Role;
import com.loop.new_loop_api.users.entity.User;
import com.loop.new_loop_api.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private UserDetailsService    userDetailsService;
    @Mock private JwtService            jwtService;
    @Mock private UserRepository        userRepository;
    @Mock private AuthMapper            authMapper;
    @Mock private AuditService          auditService;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(authenticationManager, userDetailsService, jwtService, userRepository,
                authMapper, auditService);
    }

    private static LoginRequest request(String username) {
        var request = new LoginRequest();
        ReflectionTestUtils.setField(request, "username", username);
        ReflectionTestUtils.setField(request, "password", "not-the-real-one");
        return request;
    }

    @Test
    void should_auditLogin_when_credentialsAreValid() {
        var user = User.builder().id(UUID.randomUUID()).username("supervisor1").role(Role.SUPERVISOR).build();
        when(userRepository.findByUsername("supervisor1")).thenReturn(Optional.of(user));

        authService.login(request("supervisor1"));

        verify(auditService).registerLogin(user.getId(), "supervisor1", "SUPERVISOR");
        verify(auditService, never()).registerFailedLogin(anyString(), anyString());
    }

    @Test
    void should_auditFailedLoginAndRethrow_when_credentialsAreWrong() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));
        when(userRepository.existsByUsername("chofer9")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request("chofer9"))).isInstanceOf(BadCredentialsException.class);

        verify(auditService).registerFailedLogin("chofer9", "BAD_CREDENTIALS");
        verify(auditService, never()).registerLogin(any(), anyString(), anyString());
    }

    @Test
    void should_auditFailedLoginAsDisabled_when_userIsInactive() {
        when(authenticationManager.authenticate(any())).thenThrow(new DisabledException("disabled"));
        when(userRepository.existsByUsername("ex.empleado")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request("ex.empleado"))).isInstanceOf(DisabledException.class);

        verify(auditService).registerFailedLogin("ex.empleado", "USER_DISABLED");
    }

    @Test
    void should_notStoreTheTypedUsername_when_itMatchesNoUser() {
        // People often type the password in the username box: an unknown username is never persisted.
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));
        when(userRepository.existsByUsername("MiClave123")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request("MiClave123"))).isInstanceOf(BadCredentialsException.class);

        verify(auditService).registerFailedLogin(null, "UNKNOWN_USER");
    }

    @Test
    void should_keepThe401_when_auditingTheFailedLoginFails() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));
        when(userRepository.existsByUsername("chofer9")).thenReturn(true);
        doThrow(new IllegalStateException("db down")).when(auditService).registerFailedLogin(anyString(), anyString());

        assertThatThrownBy(() -> authService.login(request("chofer9")))
                .isInstanceOf(BadCredentialsException.class)
                .satisfies(ex -> assertThat(ex.getSuppressed()).hasSize(1));
    }
}

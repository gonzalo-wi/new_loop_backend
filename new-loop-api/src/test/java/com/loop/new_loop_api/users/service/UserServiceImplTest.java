package com.loop.new_loop_api.users.service;

import com.loop.new_loop_api.audit.service.iService.AuditService;
import com.loop.new_loop_api.branches.repository.BranchRepository;
import com.loop.new_loop_api.common.security.AuthenticatedUserCache;
import com.loop.new_loop_api.users.dto.UpdateUserRequest;
import com.loop.new_loop_api.users.dto.UserResponse;
import com.loop.new_loop_api.users.entity.User;
import com.loop.new_loop_api.users.mapper.UserMapper;
import com.loop.new_loop_api.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserRepository   userRepository;
    @Mock private UserMapper       userMapper;
    @Mock private BranchRepository branchRepository;
    @Mock private PasswordEncoder  passwordEncoder;
    @Mock private AuditService     auditService;
    @Mock private AuthenticatedUserCache authenticatedUserCache;

    @InjectMocks private UserServiceImpl userService;

    @Test
    void getAllUsers_trimsSearch_dropsSort_andMapsResults() {
        var user     = new User();
        var response = UserResponse.builder().id(UUID.randomUUID()).name("Rto 2").build();
        Page<User> repoPage = new PageImpl<>(List.of(user));

        var captor = ArgumentCaptor.forClass(Pageable.class);
        when(userRepository.searchUsers(eq("rto"), captor.capture())).thenReturn(repoPage);
        when(userMapper.toResponse(user)).thenReturn(response);

        var result = userService.getAllUsers("  rto  ",
                PageRequest.of(1, 50, Sort.by("name").descending()));

        assertThat(result.getContent()).containsExactly(response);

        var forwarded = captor.getValue();
        assertThat(forwarded.getPageNumber()).isEqualTo(1);
        assertThat(forwarded.getPageSize()).isEqualTo(50);
        assertThat(forwarded.getSort().isSorted())
                .as("Pageable sort must be dropped; ordering lives in the native query")
                .isFalse();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void getAllUsers_normalizesBlankSearchToNull(String rawSearch) {
        when(userRepository.searchUsers(eq(null), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageImpl<>(List.of()));

        userService.getAllUsers(rawSearch, PageRequest.of(0, 20));

        org.mockito.Mockito.verify(userRepository)
                .searchUsers(eq(null), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deactivateUser_invalidatesCachedSession() {
        var user = existingUser();

        userService.deactivateUser(user.getId());

        verify(authenticatedUserCache).invalidate(user.getUsername());
    }

    @Test
    void activateUser_invalidatesCachedSession() {
        var user = existingUser();

        userService.activateUser(user.getId());

        verify(authenticatedUserCache).invalidate(user.getUsername());
    }

    @Test
    void updateUser_invalidatesCachedSession() {
        var user = existingUser();
        when(userRepository.save(user)).thenReturn(user);

        userService.updateUser(user.getId(), new UpdateUserRequest());

        verify(authenticatedUserCache).invalidate(user.getUsername());
    }

    private User existingUser() {
        var user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("juan.perez");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        return user;
    }
}

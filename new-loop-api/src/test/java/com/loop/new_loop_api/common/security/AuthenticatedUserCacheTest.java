package com.loop.new_loop_api.common.security;

import com.github.benmanes.caffeine.cache.Ticker;
import com.loop.new_loop_api.users.entity.Role;
import com.loop.new_loop_api.users.entity.User;
import com.loop.new_loop_api.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticatedUserCacheTest {

    private static final String   USERNAME = "juan.perez";
    private static final Duration TTL      = Duration.ofMinutes(2);

    @Mock private UserRepository userRepository;

    private final AtomicLong nanos  = new AtomicLong();
    private final Ticker     ticker = nanos::get;

    private AuthenticatedUserCache cache;

    @BeforeEach
    void setUp() {
        cache = new AuthenticatedUserCache(userRepository, TTL, ticker);
    }

    @Test
    void shouldQueryRepositoryOnceForRepeatedLookupsWithinTtl() {
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user(true)));

        cache.findActive(USERNAME);
        cache.findActive(USERNAME);
        var result = cache.findActive(USERNAME);

        assertThat(result).get().extracting(AuthenticatedUser::username).isEqualTo(USERNAME);
        verify(userRepository, times(1)).findByUsername(USERNAME);
    }

    @Test
    void shouldReloadUserAfterTtlExpires() {
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user(true)));

        cache.findActive(USERNAME);
        nanos.addAndGet(TTL.plusSeconds(1).toNanos());
        cache.findActive(USERNAME);

        verify(userRepository, times(2)).findByUsername(USERNAME);
    }

    @Test
    void shouldNotReturnInactiveUser() {
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user(false)));

        assertThat(cache.findActive(USERNAME)).isEmpty();
    }

    @Test
    void shouldLoseAccessWhenDeactivatedOnceTtlExpires() {
        when(userRepository.findByUsername(USERNAME))
                .thenReturn(Optional.of(user(true)))
                .thenReturn(Optional.of(user(false)));

        assertThat(cache.findActive(USERNAME)).isPresent();
        nanos.addAndGet(TTL.plusSeconds(1).toNanos());

        assertThat(cache.findActive(USERNAME)).isEmpty();
    }

    @Test
    void shouldReloadUserAfterInvalidate() {
        when(userRepository.findByUsername(USERNAME))
                .thenReturn(Optional.of(user(true)))
                .thenReturn(Optional.of(user(false)));

        assertThat(cache.findActive(USERNAME)).isPresent();
        cache.invalidate(USERNAME);

        assertThat(cache.findActive(USERNAME)).isEmpty();
    }

    @Test
    void shouldNotCacheUnknownUsername() {
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

        assertThat(cache.findActive(USERNAME)).isEmpty();
        assertThat(cache.findActive(USERNAME)).isEmpty();

        verify(userRepository, times(2)).findByUsername(USERNAME);
    }

    private User user(boolean active) {
        var user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(USERNAME);
        user.setName("Juan Pérez");
        user.setRole(Role.REPARTIDOR);
        user.setActive(active);
        return user;
    }
}

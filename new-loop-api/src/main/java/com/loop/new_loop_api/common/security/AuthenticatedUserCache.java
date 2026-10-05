package com.loop.new_loop_api.common.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.loop.new_loop_api.users.entity.User;
import com.loop.new_loop_api.users.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Short-lived cache of the user data the JWT filter needs, so polling clients don't hit the
 * users table on every request. Entries expire after the TTL and are invalidated by UserService
 * on edit/activate/deactivate, so a deactivated user loses access at most one TTL later.
 */
@Component
public class AuthenticatedUserCache {

    private final UserRepository            userRepository;
    private final Cache<String, CachedUser> cache;

    @Autowired
    public AuthenticatedUserCache(UserRepository userRepository,
                                  @Value("${app.security.user-cache.ttl}") Duration ttl,
                                  @Value("${app.security.user-cache.max-size}") long maxSize) {
        this(userRepository, ttl, maxSize, Ticker.systemTicker());
    }

    AuthenticatedUserCache(UserRepository userRepository, Duration ttl, Ticker ticker) {
        this(userRepository, ttl, Long.MAX_VALUE, ticker);
    }

    private AuthenticatedUserCache(UserRepository userRepository, Duration ttl, long maxSize, Ticker ticker) {
        this.userRepository = userRepository;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maxSize)
                .ticker(ticker)
                .build();
    }

    /** Returns the principal for an existing, active user; unknown usernames are not cached. */
    public Optional<AuthenticatedUser> findActive(String username) {
        var cached = cache.get(username, this::load);
        return Optional.ofNullable(cached)
                .filter(CachedUser::active)
                .map(CachedUser::principal);
    }

    public void invalidate(String username) {
        cache.invalidate(username);
    }

    private CachedUser load(String username) {
        return userRepository.findByUsername(username)
                .map(AuthenticatedUserCache::toCachedUser)
                .orElse(null);
    }

    private static CachedUser toCachedUser(User user) {
        var branchId  = user.getBranch() != null ? user.getBranch().getId() : null;
        var principal = new AuthenticatedUser(user.getId(), user.getUsername(), user.getName(), user.getRole(), branchId);
        return new CachedUser(principal, Boolean.TRUE.equals(user.getActive()));
    }

    private record CachedUser(AuthenticatedUser principal, boolean active) {
    }
}

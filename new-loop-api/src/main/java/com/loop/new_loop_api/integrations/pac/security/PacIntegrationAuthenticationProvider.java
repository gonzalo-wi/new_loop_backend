package com.loop.new_loop_api.integrations.pac.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * Authenticates the single PAC integration account. It is the only provider of its chain (no parent manager),
 * so a LOOP user's username/password can never be accepted here.
 */
@Slf4j
public class PacIntegrationAuthenticationProvider implements AuthenticationProvider {

    public static final String ROLE = "ROLE_PAC_INTEGRATION";

    private static final String UNKNOWN_ADDRESS = "unknown";

    private final PacIntegrationCredentials credentials;

    public PacIntegrationAuthenticationProvider(PacIntegrationCredentials credentials) {
        this.credentials = credentials;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        var username = authentication.getName();
        var password = authentication.getCredentials() == null ? null : authentication.getCredentials().toString();
        if (!credentials.matches(username, password)) {
            // Only the caller's address: the supplied values may be a real password typed in the wrong place.
            log.warn("Rejected PAC integration credentials from {}", remoteAddress());
            throw new BadCredentialsException("Invalid integration credentials");
        }
        return UsernamePasswordAuthenticationToken.authenticated(username, null,
                List.of(new SimpleGrantedAuthority(ROLE)));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private static String remoteAddress() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getRemoteAddr();
        }
        return UNKNOWN_ADDRESS;
    }
}

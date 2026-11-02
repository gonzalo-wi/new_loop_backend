package com.loop.new_loop_api.common.config;

import com.loop.new_loop_api.common.security.JsonAuthErrorHandler;
import com.loop.new_loop_api.common.security.JwtAuthenticationFilter;
import com.loop.new_loop_api.integrations.pac.security.PacIntegrationAuthenticationProvider;
import com.loop.new_loop_api.integrations.pac.security.PacIntegrationCredentials;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String PAC_INTEGRATION_MATCHER       = "/integrations/pac/**";
    private static final String PAC_INVALID_CREDENTIALS_MESSAGE = "Invalid integration credentials";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JsonAuthErrorHandler    jsonAuthErrorHandler;

    @Value("${management.security.username}")
    private String actuatorUsername;

    @Value("${management.security.password}")
    private String actuatorPassword;

    // Empty on purpose when unset: no fallback password, the endpoint is simply disabled.
    @Value("${integrations.pac.username:}")
    private String pacUsername;

    @Value("${integrations.pac.password:}")
    private String pacPassword;

    /**
     * Dedicated chain for /actuator/** with its own local DaoAuthenticationProvider, so the
     * monitoring credentials never become an application-wide UserDetailsService bean — that
     * would collide with the app's own UserDetailsService (used for JWT login) on type-based
     * injection, as it did when both were exposed as beans.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http, PasswordEncoder passwordEncoder) throws Exception {
        var actuatorUser = User.withUsername(actuatorUsername)
                .password(passwordEncoder.encode(actuatorPassword))
                .roles("ACTUATOR")
                .build();
        var actuatorAuthProvider = new DaoAuthenticationProvider(new InMemoryUserDetailsManager(actuatorUser));
        actuatorAuthProvider.setPasswordEncoder(passwordEncoder);

        http
                .securityMatcher("/actuator/**")
                .csrf(AbstractHttpConfigurer::disable)
                .authenticationProvider(actuatorAuthProvider)
                .authorizeHttpRequests(auth -> auth
                        // /actuator/health stays public for infra/Docker healthchecks.
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // Rest of /actuator/** (prometheus, metrics, info) requires HTTP Basic auth with
                        // dedicated monitoring credentials, so scrapers (Prometheus) don't need a user JWT.
                        .anyRequest().authenticated()
                )
                .httpBasic(basic -> {});
        return http.build();
    }

    /**
     * Dedicated chain for PAC's backend: HTTP Basic with the integration credentials only. Its ProviderManager has
     * no parent, otherwise a rejected PAC login would fall back to the global manager and a LOOP user's
     * username/password would pass. No JWT filter here, so a user token stays anonymous and gets 401.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain pacIntegrationSecurityFilterChain(HttpSecurity http) throws Exception {
        var credentials = new PacIntegrationCredentials(pacUsername, pacPassword);
        if (!credentials.isConfigured()) {
            log.warn("PAC integration disabled: credentials not set (integrations.pac.username / password)");
        }
        var entryPoint = jsonAuthErrorHandler.withMessage(PAC_INVALID_CREDENTIALS_MESSAGE);

        http
                .securityMatcher(PAC_INTEGRATION_MATCHER)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationManager(new ProviderManager(new PacIntegrationAuthenticationProvider(credentials)))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .httpBasic(basic -> basic.authenticationEntryPoint(entryPoint))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint));
        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain appSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                // JWT only: without this, every anonymous 401 would save the request in a new HttpSession.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Default deny: only what must work before logging in is public. An invalid or expired token
                // leaves the request anonymous, so it still reaches the public endpoints (a stale token kept on
                // a phone must not block the login) and gets 401 everywhere else.
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/health", "/app/version", "/app/download/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/app/version").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(jsonAuthErrorHandler)
                        .accessDeniedHandler(jsonAuthErrorHandler)
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}

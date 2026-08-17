package com.loop.new_loop_api.common.config;

import com.loop.new_loop_api.common.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${management.security.username}")
    private String actuatorUsername;

    @Value("${management.security.password}")
    private String actuatorPassword;

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

    @Bean
    @Order(2)
    public SecurityFilterChain appSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
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

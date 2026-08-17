package com.loop.new_loop_api.common.config;

import com.loop.new_loop_api.common.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
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

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, PasswordEncoder passwordEncoder) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // /actuator/health stays public for infra/Docker healthchecks.
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // Rest of /actuator/** (prometheus, metrics, info) requires HTTP Basic auth with
                        // dedicated monitoring credentials, so scrapers (Prometheus) don't need a user JWT.
                        .requestMatchers("/actuator/**").authenticated()
                        .anyRequest().permitAll()
                )
                .httpBasic(basic -> {})
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** Dedicated in-memory user for Basic-auth scraping of /actuator/** (Prometheus), separate from the JWT-based user auth. */
    @Bean
    public UserDetailsService actuatorUserDetailsService(PasswordEncoder passwordEncoder) {
        var actuatorUser = User.withUsername(actuatorUsername)
                .password(passwordEncoder.encode(actuatorPassword))
                .roles("ACTUATOR")
                .build();
        return new InMemoryUserDetailsManager(actuatorUser);
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

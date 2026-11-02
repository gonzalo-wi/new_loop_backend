package com.loop.new_loop_api.integrations.pac.security;

import com.loop.new_loop_api.common.config.SecurityConfig;
import com.loop.new_loop_api.common.metrics.ErrorMetrics;
import com.loop.new_loop_api.common.security.AuthenticatedUser;
import com.loop.new_loop_api.common.security.AuthenticatedUserCache;
import com.loop.new_loop_api.common.security.JsonAuthErrorHandler;
import com.loop.new_loop_api.common.security.JwtAuthenticationFilter;
import com.loop.new_loop_api.common.security.JwtService;
import com.loop.new_loop_api.integrations.pac.controller.PacArrivalController;
import com.loop.new_loop_api.integrations.pac.service.iService.PacArrivalService;
import com.loop.new_loop_api.users.controller.UserController;
import com.loop.new_loop_api.users.entity.Role;
import com.loop.new_loop_api.users.service.iService.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The real PAC chain (and the app chain behind it) against the real controllers, with mocked services. */
@WebMvcTest(controllers = {PacArrivalController.class, UserController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JsonAuthErrorHandler.class, JwtService.class,
        PacIntegrationSecurityTest.TestBeans.class})
@TestPropertySource(properties = {
        "app.jwt.secret=" + PacIntegrationSecurityTest.SECRET,
        "management.security.username=monitor",
        "management.security.password=monitor",
        "integrations.pac.username=pac",
        "integrations.pac.password=secret"
})
class PacIntegrationSecurityTest {

    // Any 256-bit base64 key; only for tests.
    static final String SECRET = "dGVzdC1zZWNyZXQtdGhhdC1pcy1sb25nLWVub3VnaC1mb3ItaHMyNTY=";
    static final String ARRIVALS = "/integrations/pac/arrivals";
    static final String INVALID_MESSAGE = "Invalid integration credentials";

    static class TestBeans {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        // A LOOP user whose own username/password must NOT work over Basic on the PAC chain.
        @Bean
        UserDetailsService loopUsers(PasswordEncoder encoder) {
            return new InMemoryUserDetailsManager(
                    User.withUsername("loopuser").password(encoder.encode("looppass")).roles("ADMIN").build());
        }
    }

    static String basic(String user, String password) {
        var raw = (user + ":" + password).getBytes(StandardCharsets.UTF_8);
        return "Basic " + Base64.getEncoder().encodeToString(raw);
    }

    @Autowired private MockMvc    mockMvc;
    @Autowired private JwtService jwtService;

    @MockitoBean private AuthenticatedUserCache authenticatedUserCache;
    @MockitoBean private UserService            userService;
    @MockitoBean private PacArrivalService      pacArrivalService;
    @MockitoBean private ErrorMetrics           errorMetrics;

    @Test
    void shouldLetTheRequestThrough_when_theBasicCredentialsAreTheIntegrationOnes() throws Exception {
        mockMvc.perform(get(ARRIVALS).header("Authorization", basic("pac", "secret")))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void shouldAnswer401WithTheMessage_when_thereAreNoCredentials() throws Exception {
        mockMvc.perform(get(ARRIVALS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value(INVALID_MESSAGE))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void shouldAnswer401_when_thePasswordIsWrong() throws Exception {
        mockMvc.perform(get(ARRIVALS).header("Authorization", basic("pac", "nope")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_MESSAGE))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void shouldAnswer401_when_theUserIsWrong() throws Exception {
        mockMvc.perform(get(ARRIVALS).header("Authorization", basic("someone", "secret")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_MESSAGE));
    }

    @Test
    void shouldAnswer401_when_aValidUserJwtIsSent() throws Exception {
        when(authenticatedUserCache.findActive("boss"))
                .thenReturn(Optional.of(new AuthenticatedUser(UUID.randomUUID(), "boss", "boss", Role.ADMIN, null)));
        var token = jwtService.generateToken(
                (User) User.withUsername("boss").password("x").authorities(List.of()).build(), Map.of("role", "ADMIN"));

        mockMvc.perform(get(ARRIVALS).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_MESSAGE));
    }

    @Test
    void shouldAnswer401_when_aLoopUserSendsItsOwnCredentialsOverBasic() throws Exception {
        mockMvc.perform(get(ARRIVALS).header("Authorization", basic("loopuser", "looppass")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldNotAcceptTheIntegrationCredentialsOnAnyOtherEndpoint() throws Exception {
        mockMvc.perform(get("/users").header("Authorization", basic("pac", "secret")))
                .andExpect(status().isUnauthorized());
    }
}

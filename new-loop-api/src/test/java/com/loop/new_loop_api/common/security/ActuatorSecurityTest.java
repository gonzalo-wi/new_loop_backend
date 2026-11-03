package com.loop.new_loop_api.common.security;

import com.loop.new_loop_api.common.config.SecurityConfig;
import com.loop.new_loop_api.common.metrics.ErrorMetrics;
import com.loop.new_loop_api.users.controller.UserController;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The real actuator chain: only the monitoring credentials unlock it, never a LOOP user's. */
@WebMvcTest(controllers = UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JsonAuthErrorHandler.class, JwtService.class,
        ActuatorSecurityTest.TestBeans.class})
@TestPropertySource(properties = {
        "app.jwt.secret=" + ActuatorSecurityTest.SECRET,
        "management.security.username=monitor",
        "management.security.password=monitor-pass",
        "integrations.pac.username=pac",
        "integrations.pac.password=secret"
})
class ActuatorSecurityTest {

    static final String SECRET = "dGVzdC1zZWNyZXQtdGhhdC1pcy1sb25nLWVub3VnaC1mb3ItaHMyNTY=";

    @RestController
    static class FakeActuator {
        @GetMapping("/actuator/prometheus")
        String prometheus() {
            return "ok";
        }

        @GetMapping("/actuator/metrics")
        String metrics() {
            return "ok";
        }

        @GetMapping("/actuator/health")
        String health() {
            return "UP";
        }
    }

    static class TestBeans {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        FakeActuator fakeActuator() {
            return new FakeActuator();
        }

        // A LOOP user whose own username/password must NOT work over Basic on the actuator chain.
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

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AuthenticatedUserCache authenticatedUserCache;
    @MockitoBean private UserService            userService;
    @MockitoBean private ErrorMetrics           errorMetrics;

    @Test
    void shouldAnswer401_when_aLoopUserSendsItsOwnCredentialsToPrometheus() throws Exception {
        mockMvc.perform(get("/actuator/prometheus").header("Authorization", basic("loopuser", "looppass")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAnswer401_when_aLoopUserSendsItsOwnCredentialsToMetrics() throws Exception {
        mockMvc.perform(get("/actuator/metrics").header("Authorization", basic("loopuser", "looppass")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAnswer200_when_theActuatorCredentialsAreSent() throws Exception {
        mockMvc.perform(get("/actuator/prometheus").header("Authorization", basic("monitor", "monitor-pass")))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAnswer401_when_theActuatorPasswordIsWrong() throws Exception {
        mockMvc.perform(get("/actuator/prometheus").header("Authorization", basic("monitor", "nope")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAnswer401_when_thereAreNoCredentials() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldKeepHealthPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}

package com.loop.new_loop_api.integrations.pac.security;

import com.loop.new_loop_api.common.config.SecurityConfig;
import com.loop.new_loop_api.common.metrics.ErrorMetrics;
import com.loop.new_loop_api.common.security.AuthenticatedUserCache;
import com.loop.new_loop_api.common.security.JsonAuthErrorHandler;
import com.loop.new_loop_api.common.security.JwtAuthenticationFilter;
import com.loop.new_loop_api.common.security.JwtService;
import com.loop.new_loop_api.integrations.pac.controller.PacArrivalController;
import com.loop.new_loop_api.integrations.pac.service.iService.PacArrivalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.loop.new_loop_api.integrations.pac.security.PacIntegrationSecurityTest.ARRIVALS;
import static com.loop.new_loop_api.integrations.pac.security.PacIntegrationSecurityTest.INVALID_MESSAGE;
import static com.loop.new_loop_api.integrations.pac.security.PacIntegrationSecurityTest.basic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Blank credentials disable the endpoint (every call is 401) without stopping the application from starting. */
@WebMvcTest(controllers = PacArrivalController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JsonAuthErrorHandler.class, JwtService.class,
        PacIntegrationSecurityTest.TestBeans.class})
@TestPropertySource(properties = {
        "app.jwt.secret=" + PacIntegrationSecurityTest.SECRET,
        "management.security.username=monitor",
        "management.security.password=monitor",
        "integrations.pac.username=   ",
        "integrations.pac.password=   "
})
class PacIntegrationDisabledSecurityTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AuthenticatedUserCache authenticatedUserCache;
    @MockitoBean private PacArrivalService      pacArrivalService;
    @MockitoBean private ErrorMetrics           errorMetrics;

    @Test
    void shouldRefuseWhitespaceCredentials_when_theConfiguredOnesAreWhitespace() throws Exception {
        mockMvc.perform(get(ARRIVALS).header("Authorization", basic("   ", "   ")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_MESSAGE));
    }

    @Test
    void shouldRefuseEmptyCredentialsAndAnonymousCalls_when_theConfiguredOnesAreBlank() throws Exception {
        mockMvc.perform(get(ARRIVALS).header("Authorization", basic("", "")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(ARRIVALS))
                .andExpect(status().isUnauthorized());
    }
}

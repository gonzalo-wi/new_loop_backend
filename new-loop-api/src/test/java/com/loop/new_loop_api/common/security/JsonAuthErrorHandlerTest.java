package com.loop.new_loop_api.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class JsonAuthErrorHandlerTest {

    private final JsonAuthErrorHandler handler = new JsonAuthErrorHandler();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void shouldWriteTheSameApiErrorBodyWithTheGivenMessage() throws Exception {
        var request  = new MockHttpServletRequest("GET", "/integrations/pac/arrivals");
        var response = new MockHttpServletResponse();

        handler.withMessage("Invalid integration credentials")
                .commence(request, response, new BadCredentialsException("x"));

        var body = json.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("error").asText()).isEqualTo("Unauthorized");
        assertThat(body.get("message").asText()).isEqualTo("Invalid integration credentials");
        assertThat(body.get("path").asText()).isEqualTo("/integrations/pac/arrivals");
        assertThat(body.has("timestamp")).isTrue();
    }

    @Test
    void shouldKeepTheDefaultMessage_when_theHandlerIsUsedDirectly() throws Exception {
        var response = new MockHttpServletResponse();

        handler.commence(new MockHttpServletRequest("GET", "/users"), response, new BadCredentialsException("x"));

        assertThat(json.readTree(response.getContentAsString()).get("message").asText())
                .startsWith("Authentication required");
    }
}

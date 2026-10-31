package com.loop.new_loop_api.common.security;

import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    // Any 256-bit base64 key; only for tests.
    private static final String SECRET = "dGVzdC1zZWNyZXQtdGhhdC1pcy1sb25nLWVub3VnaC1mb3ItaHMyNTY=";

    private static final Instant LOGIN = Instant.parse("2026-10-30T11:00:00Z");    // 08:00 in Argentina
    private static final Instant MIDNIGHT = Instant.parse("2026-10-31T03:00:00Z"); // 00:00 in Argentina

    private static JwtService serviceAt(Instant now) {
        return new JwtService(SECRET, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static String tokenIssuedAtLogin() {
        return serviceAt(LOGIN).generateToken(User.withUsername("ctrl").password("x").authorities(List.of()).build(),
                Map.of("role", "CONTROLADOR"));
    }

    @Test
    void shouldIssueATokenThatExpiresAtTheNextMidnight() {
        assertThat(serviceAt(LOGIN).extractExpiration(tokenIssuedAtLogin())).isEqualTo(MIDNIGHT);
    }

    @Test
    void shouldAcceptTheToken_when_itIsStillBeforeMidnight() {
        assertThat(serviceAt(MIDNIGHT.minusSeconds(60)).extractUsername(tokenIssuedAtLogin())).isEqualTo("ctrl");
    }

    @Test
    void shouldRejectTheToken_when_midnightHasPassed() {
        var token = tokenIssuedAtLogin();
        var afterMidnight = serviceAt(MIDNIGHT.plusSeconds(60));

        assertThatThrownBy(() -> afterMidnight.extractUsername(token)).isInstanceOf(ExpiredJwtException.class);
    }
}

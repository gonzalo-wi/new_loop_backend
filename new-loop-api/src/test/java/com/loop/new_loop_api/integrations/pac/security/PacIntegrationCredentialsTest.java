package com.loop.new_loop_api.integrations.pac.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PacIntegrationCredentialsTest {

    private final PacIntegrationCredentials credentials = new PacIntegrationCredentials("pac", "secret");

    @Test
    void shouldMatch_when_userAndPasswordAreTheConfiguredOnes() {
        assertThat(credentials.isConfigured()).isTrue();
        assertThat(credentials.matches("pac", "secret")).isTrue();
    }

    @Test
    void shouldNotMatch_when_theUserIsWrong() {
        assertThat(credentials.matches("other", "secret")).isFalse();
    }

    @Test
    void shouldNotMatch_when_thePasswordIsWrong() {
        assertThat(credentials.matches("pac", "Secret")).isFalse();
    }

    @Test
    void shouldNotMatch_when_theSuppliedValuesHaveOtherLengths() {
        assertThat(credentials.matches("pac", "secret-and-more")).isFalse();
        assertThat(credentials.matches("pa", "secret")).isFalse();
        assertThat(credentials.matches("", "")).isFalse();
    }

    @Test
    void shouldNotMatch_when_theSuppliedValuesAreNull() {
        assertThat(credentials.matches(null, "secret")).isFalse();
        assertThat(credentials.matches("pac", null)).isFalse();
    }

    @Test
    void shouldNeverMatch_when_theConfiguredValuesAreNullBlankOrWhitespace() {
        var unset = new PacIntegrationCredentials[]{
                new PacIntegrationCredentials(null, null),
                new PacIntegrationCredentials("", ""),
                new PacIntegrationCredentials("   ", "   "),
                new PacIntegrationCredentials("pac", ""),
                new PacIntegrationCredentials("", "secret"),
                new PacIntegrationCredentials("pac", "  ")
        };

        for (var candidate : unset) {
            assertThat(candidate.isConfigured()).isFalse();
            assertThat(candidate.matches("pac", "secret")).isFalse();
            assertThat(candidate.matches("", "")).isFalse();
            assertThat(candidate.matches("   ", "   ")).isFalse();
            assertThat(candidate.matches(null, null)).isFalse();
        }
    }
}

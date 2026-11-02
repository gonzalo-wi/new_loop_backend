package com.loop.new_loop_api.integrations.pac.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * The username/password PAC uses on /integrations/pac/**. Unset, blank or whitespace-only values mean the
 * integration is disabled: nothing matches. Values are compared by their SHA-256 digest with
 * {@link MessageDigest#isEqual}, which takes the same time whatever the input and hides the length.
 */
public final class PacIntegrationCredentials {

    private static final String DIGEST_ALGORITHM = "SHA-256";

    private final String username;
    private final String password;

    public PacIntegrationCredentials(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public boolean isConfigured() {
        return hasText(username) && hasText(password);
    }

    public boolean matches(String suppliedUsername, String suppliedPassword) {
        if (!isConfigured()) return false;
        // Both are always evaluated: no early exit that would tell which one was wrong.
        boolean usernameMatches = digestEquals(username, suppliedUsername);
        boolean passwordMatches = digestEquals(password, suppliedPassword);
        return usernameMatches & passwordMatches;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean digestEquals(String expected, String supplied) {
        if (supplied == null) return false;
        return MessageDigest.isEqual(sha256(expected), sha256(supplied));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance(DIGEST_ALGORITHM).digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // Every JVM must provide SHA-256, so this cannot happen.
            throw new IllegalStateException("Digest algorithm not available: " + DIGEST_ALGORITHM, e);
        }
    }
}

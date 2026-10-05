package com.loop.new_loop_api.auth.service;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;

/** Why a login was rejected, as stored in the audit log. */
public enum LoginFailureReason {
    BAD_CREDENTIALS,
    /** The typed username matches no user; it is not stored, since people often type the password there. */
    UNKNOWN_USER,
    USER_DISABLED,
    AUTHENTICATION_FAILED;

    static LoginFailureReason of(AuthenticationException ex) {
        if (ex instanceof BadCredentialsException) return BAD_CREDENTIALS;
        if (ex instanceof DisabledException)       return USER_DISABLED;
        return AUTHENTICATION_FAILED;
    }
}

package com.sma.admin.auth;

/** Additional verification only; never replaces authorization, TOTP, or the human session. */
public interface SecurityPasswordVerification {
    /** sessionId must come from the authenticated server request, not a client assertion. Throws on failure. */
    void verifyForCurrentSession(long sessionId, String candidate);
}

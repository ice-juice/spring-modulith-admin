package com.sma.admin.identity;
import com.sma.admin.common.ErrorCode;
public enum IdentityErrorCode implements ErrorCode {
    CONFIGURATION_NOT_FOUND("identity.error.configuration-not-found",404),
    INVALID_CONFIGURATION("identity.error.invalid-configuration",400),
    DUPLICATE_CODE("identity.error.duplicate-code",409),
    VERSION_CONFLICT("identity.error.version-conflict",409),
    INVALID_GOOGLE_PROOF("identity.error.invalid-google-proof",401),
    PROVIDER_UNAVAILABLE("identity.error.provider-unavailable",503),
    INVALID_SESSION("identity.error.invalid-session",401),
    REGISTRATION_DISABLED("identity.error.registration-disabled",403),
    SESSION_LIMIT("identity.error.session-limit",429),
    LOGIN_LIMIT("identity.error.login-limit",429),
    LOGIN_EXPIRED("identity.error.login-expired",401),
    LOGIN_USED("identity.error.login-used",409),
    INVALID_EMAIL("identity.error.invalid-email",400),
    INVALID_EMAIL_CHALLENGE("identity.error.invalid-email-challenge",401),
    EMAIL_CHALLENGE_LIMIT("identity.error.email-challenge-limit",429),
    EMAIL_CHALLENGE_USED("identity.error.email-challenge-used",409),
    EMAIL_CHALLENGE_EXPIRED("identity.error.email-challenge-expired",401),
    EMAIL_NOT_CONFIGURED("identity.error.email-not-configured",503),
    PASSWORD_FORMAT("identity.error.password-format",400),
    WEAK_PASSWORD("identity.error.weak-password",400),
    EMAIL_ACCOUNT_CONFLICT("identity.error.email-account-conflict",409),
    INVALID_PASSWORD_LOGIN("identity.error.invalid-password-login",401),
    INVALID_REAUTHENTICATION("identity.error.invalid-reauthentication",401),
    REAUTHENTICATION_LIMIT("identity.error.reauthentication-limit",429),
    IDENTITY_LINK_CONFLICT("identity.error.identity-link-conflict",409),
    IDENTITY_LINK_LIMIT("identity.error.identity-link-limit",429),
    REVOKED_IDENTITY_LINK("identity.error.revoked-identity-link",401),
    LAST_AUTHENTICATION_METHOD("identity.error.last-authentication-method",409),
    PASSWORD_ALREADY_CONFIGURED("identity.error.password-already-configured",409);
    private final String key;private final int status;
    IdentityErrorCode(String key,int status){this.key=key;this.status=status;}
    @Override public String key(){return key;}
    @Override public int status(){return status;}
}

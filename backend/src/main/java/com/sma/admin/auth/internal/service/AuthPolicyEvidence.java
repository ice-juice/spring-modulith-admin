package com.sma.admin.auth.internal.service;
import org.springframework.stereotype.Component;
import com.sma.admin.common.deployment.*;
@Component
public class AuthPolicyEvidence implements DeploymentEvidenceSource {
    private final AuthProperties auth;private final AccountSecurityProperties security;
    public AuthPolicyEvidence(AuthProperties auth,AccountSecurityProperties security){this.auth=auth;this.security=security;}
    @Override public String id(){return "auth.session-policy";}
    @Override public DeploymentEvidence.Proof collect(DeploymentEvidence.Challenge challenge){
        DeploymentEvidence.requireHost();
        return new DeploymentFields().text("protocol","1").duration("session-ttl",auth.sessionTtl()).duration("remember-ttl",auth.rememberTtl())
                .flag("single-terminal-default",auth.singleTerminal()).duration("auto-lock-default",auth.autoLockAfter())
                .number("password-notice-days",auth.passwordNoticeDays()).text("totp-issuer",security.issuer()).proof(id(),challenge);
    }
}

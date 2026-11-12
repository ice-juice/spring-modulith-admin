package com.sma.admin.identity.internal.service;
import org.springframework.stereotype.Component;
import com.sma.admin.common.deployment.*;
@Component
public class IdentityPolicyEvidence implements DeploymentEvidenceSource {
    private final IdentitySessionProperties sessions;private final IdentityHttpProperties http;
    private final IdentityLoginProperties login;private final IdentityEmailProperties email;
    public IdentityPolicyEvidence(IdentitySessionProperties sessions,IdentityHttpProperties http,IdentityLoginProperties login,IdentityEmailProperties email){
        this.sessions=sessions;this.http=http;this.login=login;this.email=email;
    }
    @Override public String id(){return "identity.session-policy";}
    @Override public DeploymentEvidence.Proof collect(DeploymentEvidence.Challenge challenge){
        DeploymentEvidence.requireHost();
        return new DeploymentFields().text("protocol","1").number("sessions-per-subject",sessions.maximumPerSubject())
                .number("http.concurrent",http.maximumConcurrent()).number("http.json-bytes",http.maximumJsonBytes())
                .duration("login.lifetime",login.lifetime()).duration("login.retention",login.retention())
                .number("login.outstanding",login.maximumOutstanding()).number("login.retained",login.maximumRetained()).number("login.rate-keys",login.maximumRateKeys())
                .number("login.tenant-minute",login.tenantPerMinute()).number("login.tenant-day",login.tenantPerDay())
                .number("login.client-minute",login.clientPerMinute()).number("login.source-minute",login.sourcePerMinute())
                .duration("email.lifetime",email.lifetime()).duration("email.grant-lifetime",email.grantLifetime()).duration("email.retention",email.retention())
                .number("email.outstanding",email.maximumOutstanding()).number("email.retained",email.maximumRetained()).number("email.rate-keys",email.maximumRateKeys())
                .number("email.tenant-minute",email.tenantPerMinute()).number("email.tenant-day",email.tenantPerDay())
                .number("email.client-minute",email.clientPerMinute()).number("email.source-minute",email.sourcePerMinute()).number("email.target-day",email.targetPerDay())
                .number("email.guesses-minute",email.guessesPerMinute()).number("email.guesses-day",email.guessesPerDay())
                .number("email.source-guesses-minute",email.sourceGuessesPerMinute()).number("email.maximum-failures",email.maximumFailures()).proof(id(),challenge);
    }
}

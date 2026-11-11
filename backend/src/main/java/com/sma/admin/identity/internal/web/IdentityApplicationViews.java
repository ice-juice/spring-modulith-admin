package com.sma.admin.identity.internal.web;
import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.sma.admin.identity.internal.service.*;

final class IdentityApplicationViews {
    private IdentityApplicationViews(){}
    record Bootstrap(String name,String application,String platform,boolean googleEnabled,String googleClientId,boolean registrationEnabled,String csrfToken){}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record LoginStarted(String state,String proof,String nonce,String authorizationUri,String googleClientId,String platform,Instant expiresAt){
        @Override public String toString(){return "LoginStarted[REDACTED]";}
    }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Session(Long subjectId,Long realmId,Long clientId,String application,String mode,Instant accessExpiresAt,Instant refreshExpiresAt,
                   String csrfToken,String accessToken,String refreshToken){
        @Override public String toString(){return "ApplicationSession[REDACTED]";}
    }
    static Session issued(IdentitySessionStore.Issued value){return new Session(value.subjectId(),value.realmId(),value.clientId(),value.application(),value.mode(),
            value.accessExpiresAt(),value.refreshExpiresAt(),value.csrfToken(),value.mode().equals("NATIVE")?value.accessToken():null,value.mode().equals("NATIVE")?value.refreshToken():null);}
}
